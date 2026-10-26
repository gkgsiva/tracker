package com.gkgsiva.tracker.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gkgsiva.tracker.R
import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.system.TrackerWidget.Companion.Action
import com.gkgsiva.tracker.ui.MainActivity
import com.gkgsiva.tracker.ui.formatClock
import com.gkgsiva.tracker.ui.formatHm
import com.gkgsiva.tracker.ui.formatShort
import java.time.Duration
import java.time.LocalTime

/**
 * Ongoing notification while the day is running. Its timer is drawn by the system (a chronometer
 * counting down to the required hours, or up through the break), so it needs no updates in between
 * punches. Its buttons work from the lock screen.
 */
object StatusNotification {
    /**
     * A default-importance channel (with no sound) so phones that hide silent notifications on
     * the lock screen still show it there. Replaces the low-importance "status" channel used before 1.10.
     */
    private const val CHANNEL = "status_lock"
    private const val OLD_CHANNEL = "status"
    private const val ID = 10

    fun update(context: Context, s: DaySummary) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val live = s.status == DayStatus.INSIDE || s.status == DayStatus.ON_BREAK
        if (!SettingsStore.get(context).statusNotification || !live || !canNotify(context)) {
            nm.cancel(ID)
            return
        }
        nm.deleteNotificationChannel(OLD_CHANNEL)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Status while logged in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val now = System.currentTimeMillis()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            .setUsesChronometer(true)
            .setShowWhen(true)

        val breakText = "Break ${s.breaks.formatHm()} of ${s.breakAllowance.formatShort()}"
        if (s.status == DayStatus.INSIDE) {
            val title = if (s.wfh) "Working from home" else "Inside office"
            val untilLeave = s.expectedLeave?.let { Duration.between(LocalTime.now(), it) } ?: Duration.ZERO
            if (!untilLeave.isPositive) {
                builder.setContentTitle("$title · you can leave")
                    .setWhen(now - s.inside.toMillis())
            } else {
                // Counts down to the moment every rule is met: work hours, office hours and leave-after time.
                builder.setContentTitle("$title · ${untilLeave.formatHm()} to go")
                    .setWhen(now + untilLeave.toMillis())
                    .setChronometerCountDown(true)
            }
            builder.setContentText(
                listOfNotNull(s.expectedLeave?.let { "Leave at ${it.formatClock()}" }, if (s.wfh) null else breakText)
                    .joinToString(" · ")
            )
            if (!s.wfh) builder.addAction(0, "Start break", TrackerWidget.punchIntent(context, Action.BREAK_START))
            builder.addAction(0, "Log out", TrackerWidget.punchIntent(context, Action.LOGOUT))
        } else {
            // Counts up through the day's total break time.
            builder.setContentTitle("On break · ${s.remaining.formatHm()} still to go")
                .setWhen(now - s.breaks.toMillis())
                .setContentText(
                    listOfNotNull(
                        BreakPlans.current(context, s)?.let { "${it.length.formatShort()} break, back at ${it.end.formatClock()}" },
                        s.returnBy?.let { "Back by ${it.formatClock()} to leave at ${s.expectedLeave?.formatClock()}" }
                            ?: "$breakText · over the allowance",
                    ).joinToString(" · ")
                )
                .addAction(0, "End break", TrackerWidget.punchIntent(context, Action.BREAK_END))
        }
        nm.notify(ID, builder.build())
    }

    private fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
