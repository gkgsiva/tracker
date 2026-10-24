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
import com.gkgsiva.tracker.ui.MainActivity
import com.gkgsiva.tracker.ui.formatClock
import com.gkgsiva.tracker.ui.formatHm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

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
    private const val EXTRA_KIND = "kind"
    private val LOGIN_LEAD: Duration = Duration.ofMinutes(15)
    private val BREAK_LEAD: Duration = Duration.ofMinutes(5)

    suspend fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        listOf(LOGIN, LEAVE, BREAK, TARGET).forEach { am.cancel(pending(context, it)) }

        val settings = SettingsStore.get(context)
        val now = LocalDateTime.now()
        val today = Tracker.summary(context, now.toLocalDate(), now)

        if (settings.remindLogin) {
            // Next working day (today included) where the reminder time is still ahead.
            for (offset in 0L..14L) {
                val date = now.toLocalDate().plusDays(offset)
                val at = date.atTime(settings.rules.arriveBy).minus(LOGIN_LEAD)
                if (at <= now) continue
                val day = if (offset == 0L) today else Tracker.summary(context, date, now)
                if (day.dayOff || day.worked) continue
                set(context, am, LOGIN, at)
                break
            }
        }
        if (today.status == DayStatus.INSIDE && !today.targetMet) {
            set(context, am, TARGET, now.plus(today.remaining).plusSeconds(1))
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
                notify(context, kind, "Time to log in", "Log in before ${rules.arriveBy.formatClock()} to avoid LOP.")
            }
            LEAVE -> if (s.status == DayStatus.INSIDE && s.targetMet && now.toLocalTime() >= rules.leaveAfter) {
                notify(context, kind, "You can leave now", "${s.inside.formatHm()} inside today. Remember to log out.")
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
