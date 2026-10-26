package com.gkgsiva.tracker.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gkgsiva.tracker.R
import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.domain.DayType
import com.gkgsiva.tracker.ui.Insights
import com.gkgsiva.tracker.ui.MainActivity
import com.gkgsiva.tracker.ui.formatClock
import com.gkgsiva.tracker.ui.formatHm
import com.gkgsiva.tracker.ui.formatShort
import com.gkgsiva.tracker.ui.metText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Login, can-leave and break reminders. Uses a few one-shot alarms that are recomputed whenever
 * something changes, so nothing runs in the background in between.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val LOGIN = 1
    private const val LEAVE = 2
    private const val BREAK = 3
    /** Silent: refreshes the status notification when the required hours are reached. */
    private const val TARGET = 4
    /** Summary of the month that just ended, on the 1st. */
    private const val MONTH = 5
    /** Heads-up before a planned break ends, then again when it does. */
    private const val PLAN = 6
    private const val PLAN_CHANNEL = "breakPlan"
    private val MONTH_AT: LocalTime = LocalTime.of(10, 0)
    private const val EXTRA_KIND = "kind"
    private val LOGIN_LEAD: Duration = Duration.ofMinutes(15)
    private val BREAK_LEAD: Duration = Duration.ofMinutes(5)

    suspend fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        listOf(LOGIN, LEAVE, BREAK, TARGET, MONTH, PLAN).forEach { am.cancel(pending(context, it)) }

        val settings = SettingsStore.get(context)
        val now = LocalDateTime.now()
        val today = Tracker.summary(context, now.toLocalDate(), now)

        if (settings.monthSummary) {
            val thisMonth = now.toLocalDate().withDayOfMonth(1).atTime(MONTH_AT)
            set(context, am, MONTH, if (thisMonth > now) thisMonth else thisMonth.plusMonths(1))
        }
        if (settings.remindLogin) {
            // Next working day (today included) where the reminder time is still ahead.
            for (offset in 0L..14L) {
                val date = now.toLocalDate().plusDays(offset)
                val day = if (offset == 0L) today else Tracker.summary(context, date, now)
                // A half day has no login deadline.
                if (day.dayOff || day.worked || day.type == DayType.HALF_DAY) continue
                val at = date.atTime(day.rules.arriveBy).minus(LOGIN_LEAD)
                if (at <= now) continue
                set(context, am, LOGIN, at)
                break
            }
        }
        if (today.status == DayStatus.INSIDE) {
            today.expectedLeave?.let { set(context, am, TARGET, now.toLocalDate().atTime(it).plusSeconds(1)) }
        }
        if (settings.remindLeave && today.status == DayStatus.INSIDE) {
            today.expectedLeave?.let { set(context, am, LEAVE, now.toLocalDate().atTime(it)) }
        }
        if (settings.remindBreak && today.status == DayStatus.ON_BREAK) {
            // Warn a few minutes before the allowance runs out, then again when it does.
            today.returnBy?.let { returnBy ->
                val end = now.toLocalDate().atTime(returnBy)
                set(context, am, BREAK, if (end.minus(BREAK_LEAD) > now) end.minus(BREAK_LEAD) else end)
            }
        }
        BreakPlans.current(context, today)?.let { plan ->
            val end = now.toLocalDate().atTime(plan.end)
            val heads = end.minusMinutes(settings.breakPlanLeadMinutes.toLong())
            set(context, am, PLAN, if (heads > now) heads else end)
        }
    }

    private fun set(context: Context, am: AlarmManager, kind: Int, at: LocalDateTime) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis <= System.currentTimeMillis()) return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(context, kind))
    }

    private fun pending(context: Context, kind: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        kind,
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Checks the reminder still applies, then shows it. */
    internal suspend fun fire(context: Context, kind: Int) {
        val now = LocalDateTime.now()
        val s = Tracker.summary(context, now.toLocalDate(), now)
        val rules = s.rules
        when (kind) {
            LOGIN -> if (s.status == DayStatus.NOT_STARTED) {
                // Mention a recent day left without a logout, so it gets filled in while it's fresh.
                val missed = (1L..7L).map { now.toLocalDate().minusDays(it) }
                    .firstOrNull { Tracker.summary(context, it, now).status == DayStatus.MISSING_CHECKOUT }
                val extra = missed?.let { " No logout on ${it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}: open the app to add it." }.orEmpty()
                notify(context, kind, "Time to log in", "Log in before ${rules.arriveBy.formatClock()} to avoid LOP.$extra")
            }
            LEAVE -> if (s.status == DayStatus.INSIDE && s.targetMet && now.toLocalTime() >= rules.leaveAfter) {
                notify(context, kind, "You can leave now", "${s.inside.formatHm()} worked, ${s.present.formatHm()} in the office today. Remember to log out.")
            }
            MONTH -> {
                val start = now.toLocalDate().withDayOfMonth(1).minusMonths(1)
                val end = start.plusMonths(1).minusDays(1)
                val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }
                    .associateWith { Tracker.summary(context, it, now) }
                val o = Insights.outlook(start, end, now.toLocalDate(), rules) { days.getValue(it) }
                val regularized = days.values.count { it.isRegularized }
                val text = listOfNotNull(
                    o.metText() ?: "No working days logged",
                    "${o.lopDays} LOP",
                    if (regularized > 0) "$regularized regularized" else null,
                    "${o.done.formatHm()} inside",
                ).joinToString(" · ")
                notify(context, kind, "${start.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} summary", text)
            }
            PLAN -> BreakPlans.current(context, s)?.let { plan ->
                val left = plan.left(now.toLocalTime())
                val title = if (left.seconds > 30) "Break ends in ${left.plusSeconds(30).formatShort()}"
                else "Your ${plan.length.formatShort()} break limit is up"
                planAlert(context, title, "Back at ${plan.end.formatClock()}. ${s.breaks.formatHm()} of break today.", SettingsStore.get(context).breakExtendMinutes)
            }
            BREAK -> if (s.status == DayStatus.ON_BREAK) {
                val returnBy = s.returnBy
                if (returnBy != null && s.breakLeft > Duration.ofMinutes(1)) {
                    notify(context, kind, "Break ends at ${returnBy.formatClock()}", "${s.breakLeft.formatHm()} of break allowance left.")
                } else {
                    notify(context, kind, "Break allowance used", "You've had ${s.breaks.formatHm()} of break today.")
                }
            }
        }
        Tracker.onChanged(context)
    }

    /** A heads-up notification (it pops up and makes a sound) with End break and Extend buttons. */
    private fun planAlert(context: Context, title: String, text: String, extendMinutes: Int) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(PLAN_CHANNEL, "Planned break ending", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, PLAN_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, "End break", TrackerWidget.punchIntent(context, TrackerWidget.Companion.Action.BREAK_END))
            .addAction(0, "Extend ${extendMinutes}m", TrackerWidget.extendIntent(context))
            .setAutoCancel(true)
            .build()
        nm.notify(PLAN, notification)
    }

    /** Clears the break alert once the break is over. */
    fun cancelPlanAlert(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(PLAN)
    }

    private fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notification)
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val kind = intent.getIntExtra(EXTRA_KIND, 0)
            goAsyncIo { fire(context, kind) }
        }
    }

    /** Re-arms reminders after a reboot or app update, since alarms don't survive those. */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            goAsyncIo { Tracker.onChanged(context) }
        }
    }
}

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
internal fun BroadcastReceiver.goAsyncIo(block: suspend () -> Unit) {
    val result = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            result.finish()
        }
    }
}
