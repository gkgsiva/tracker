package com.gkgsiva.tracker.system

import android.content.Context
import com.gkgsiva.tracker.data.AppDatabase
import com.gkgsiva.tracker.data.DayEntity
import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DayCalculator
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
import java.time.LocalDate
import java.time.LocalDateTime

/** Day state and punching shared by the app, the widget and reminders. */
object Tracker {

    fun parseType(name: String?): DayType? = DayType.entries.firstOrNull { it.name == name }

    /** The [DaySummary.regularized] value for a stored day. */
    fun DayEntity.regularizedNote(): String? = if (regularized) note.orEmpty() else null

    /** Summary of [date] from the database; live when [date] is today. */
    suspend fun summary(context: Context, date: LocalDate, now: LocalDateTime = LocalDateTime.now()): DaySummary {
        val dao = AppDatabase.get(context).punchDao()
        val settings = SettingsStore.get(context)
        val punches = dao.punchesFor(date.toEpochDay())
        val day = dao.day(date.toEpochDay())
        val type = DayType.effective(
            parseType(day?.type),
            date,
            hasPunches = punches.isNotEmpty(),
            weeklyOffs = settings.weeklyOffs,
        )
        return DayCalculator.summarize(
            date = date,
            punches = punches.map { it.toPunch() },
            now = if (date == now.toLocalDate()) now.toLocalTime() else null,
            rules = settings.rules,
            type = type,
            regularized = day?.regularizedNote(),
        )
    }

    /** Records a login/break end (isIn), break start or logout, now or at [at] (e.g. a gate swipe). */
    suspend fun punchNow(context: Context, isIn: Boolean, isLogout: Boolean = false, at: LocalDateTime = LocalDateTime.now()) {
        val now = at.withNano(0)
        GateMonitor.cancelPrompt(context)
        val dao = AppDatabase.get(context).punchDao()
        // Logging in on a weekly off day turns it into a working day.
        if (isIn && summary(context, now.toLocalDate(), now).type == DayType.WEEKLY_OFF) {
            dao.upsertDay(DayEntity(now.toLocalDate().toEpochDay(), DayType.OFFICE.name))
        }
        dao.insert(
            PunchEntity(
                epochDay = now.toLocalDate().toEpochDay(),
                secondOfDay = now.toLocalTime().toSecondOfDay(),
                isIn = isIn,
                isLogout = !isIn && isLogout,
            )
        )
        onChanged(context)
    }

    /** Call after any data or settings change: refreshes widgets and reminders. */
    suspend fun onChanged(context: Context) {
        TrackerWidget.updateAll(context)
        Reminders.reschedule(context)
        StatusNotification.update(context, summary(context, LocalDate.now()))
        BreakTileService.requestUpdate(context)
        Backup.autoBackupIfDue(context)
    }
}
