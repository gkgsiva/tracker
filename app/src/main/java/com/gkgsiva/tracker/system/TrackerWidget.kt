package com.gkgsiva.tracker.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.gkgsiva.tracker.R
import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.ui.MainActivity
import com.gkgsiva.tracker.ui.formatClock
import com.gkgsiva.tracker.ui.formatHm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Home-screen widget with the day's status, a live inside-time counter (a system Chronometer,
 * so it ticks without the app running) and one-tap punch buttons.
 */
class TrackerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        goAsyncIo { updateAll(context) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PUNCH) {
            val isIn = intent.getBooleanExtra(EXTRA_IN, true)
            val isLogout = intent.getBooleanExtra(EXTRA_LOGOUT, false)
            val atMillis = intent.getLongExtra(EXTRA_AT, -1L)
            val at = if (atMillis > 0) {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault())
            } else {
                LocalDateTime.now()
            }
            goAsyncIo { Tracker.punchNow(context, isIn, isLogout, at) }
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_PUNCH = "com.gkgsiva.tracker.PUNCH"
        private const val EXTRA_IN = "isIn"
        private const val EXTRA_LOGOUT = "isLogout"
        private const val EXTRA_AT = "atMillis"

        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TrackerWidget::class.java))
            if (ids.isEmpty()) return
            val views = render(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private suspend fun render(context: Context): RemoteViews {
            val s = Tracker.summary(context, LocalDate.now())
            val views = RemoteViews(context.packageName, R.layout.widget)

            views.setTextViewText(
                R.id.widget_status,
                when (s.status) {
                    DayStatus.NOT_STARTED -> "Not logged in"
                    DayStatus.INSIDE -> if (s.wfh) "Working from home" else "Inside office"
                    DayStatus.ON_BREAK -> "On break since ${s.lastOut?.formatClock()}"
                    DayStatus.DONE -> if (s.lop) "Logged out · LOP" else "Day complete"
                    DayStatus.MISSING_CHECKOUT -> "Missing logout"
                    DayStatus.DAY_OFF -> s.type.label
                },
            )
            // Everything shown is either a system-drawn timer or a fixed time, so it stays right between updates.
            val onBreak = s.status == DayStatus.ON_BREAK
            views.setChronometer(
                R.id.widget_inside,
                SystemClock.elapsedRealtime() - (if (onBreak) s.breaks else s.inside).toMillis(),
                null,
                s.status == DayStatus.INSIDE || onBreak,
            )
            val plan = BreakPlans.current(context, s)
            views.setTextViewText(
                R.id.widget_detail,
                when {
                    s.dayOff -> "No login needed"
                    onBreak -> "break today · " + (plan?.let { "back at ${it.end.formatClock()}" }
                        ?: s.returnBy?.let { "back by ${it.formatClock()}" } ?: "over the allowance")
                    s.expectedLeave != null -> "worked · leave at ${s.expectedLeave.formatClock()}"
                    else -> "worked of ${s.rules.requiredInside.formatHm()}"
                },
            )

            val (primary, secondary) = when (s.status) {
                DayStatus.NOT_STARTED, DayStatus.DONE, DayStatus.MISSING_CHECKOUT -> Action.LOGIN to null
                DayStatus.INSIDE -> if (s.wfh) Action.LOGOUT to null else Action.BREAK_START to Action.LOGOUT
                DayStatus.ON_BREAK -> Action.BREAK_END to null
                DayStatus.DAY_OFF -> null to null
            }
            bind(context, views, R.id.widget_primary, primary)
            bind(context, views, R.id.widget_secondary, secondary)

            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            return views
        }

        /** A punch the widget, status notification and quick settings tile can trigger. */
        enum class Action(val label: String, val isIn: Boolean, val isLogout: Boolean) {
            LOGIN("Log in", true, false),
            BREAK_START("Start break", false, false),
            BREAK_END("End break", true, false),
            LOGOUT("Log out", false, true),
        }

        private fun bind(context: Context, views: RemoteViews, viewId: Int, action: Action?) {
            if (action == null) {
                views.setViewVisibility(viewId, View.GONE)
                return
            }
            views.setViewVisibility(viewId, View.VISIBLE)
            views.setTextViewText(viewId, action.label)
            views.setOnClickPendingIntent(viewId, punchIntent(context, action))
        }

        /**
         * Broadcast that records [action] without opening the app: right now, or at [atMillis]
         * (epoch millis) when given, e.g. the time a notification was posted.
         */
        fun punchIntent(context: Context, action: Action, atMillis: Long? = null): PendingIntent {
            val intent = Intent(context, TrackerWidget::class.java)
                .setAction(ACTION_PUNCH)
                .putExtra(EXTRA_IN, action.isIn)
                .putExtra(EXTRA_LOGOUT, action.isLogout)
            if (atMillis != null) intent.putExtra(EXTRA_AT, atMillis)
            return PendingIntent.getBroadcast(
                context, (if (atMillis != null) 200 else 100) + action.ordinal, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
