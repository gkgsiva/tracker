package com.gkgsiva.tracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.gkgsiva.tracker.data.AppDatabase
import com.gkgsiva.tracker.data.DayEntity
import com.gkgsiva.tracker.data.EditEntity
import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.BreakLabel
import com.gkgsiva.tracker.domain.BreakPlan
import com.gkgsiva.tracker.domain.DayShape
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
import com.gkgsiva.tracker.domain.Settings
import com.gkgsiva.tracker.system.Backup
import com.gkgsiva.tracker.system.BreakPlans
import com.gkgsiva.tracker.system.SettingsStore
import com.gkgsiva.tracker.system.Tracker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class TrackerViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.punchDao()

    /** All punches grouped by day. A personal tracker stays small enough to keep in memory. */
    val punchesByDay: StateFlow<Map<LocalDate, List<PunchEntity>>> = dao.observeAll()
        .map { all -> all.groupBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Day types chosen by hand, including explicit Office (e.g. working on a weekly off). */
    val dayTypes: StateFlow<Map<LocalDate, DayType>> = dao.observeDays()
        .map { days ->
            days.mapNotNull { d ->
                Tracker.parseType(d.type)?.let { LocalDate.ofEpochDay(d.epochDay) to it }
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Regularized LOP days and their notes ("" when there's none). */
    val regularized: StateFlow<Map<LocalDate, String>> = dao.observeDays()
        .map { days -> days.filter { it.regularized }.associate { LocalDate.ofEpochDay(it.epochDay) to it.note.orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Changes made to entries by hand, oldest first, by day. */
    val edits: StateFlow<Map<LocalDate, List<EditEntity>>> = dao.observeEdits()
        .map { all -> all.groupBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Regularized days and when each was regularized (null for days from before 1.9). */
    val regularizedOn: StateFlow<Map<LocalDate, LocalDate?>> = dao.observeDays()
        .map { days -> days.filter { it.regularized }.associate { LocalDate.ofEpochDay(it.epochDay) to it.regularizedOn?.let(LocalDate::ofEpochDay) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val settings: StateFlow<Settings> = SettingsStore.flow(app)

    /** Runs a change to [date]'s entries and writes down what changed, with [reason] if given. */
    private fun logged(date: LocalDate, reason: String? = null, block: suspend () -> Unit) = change {
        db.withTransaction {
            val before = DayShape.of(dao.punchesFor(date.toEpochDay()).map { it.toPunch() })
            block()
            val after = DayShape.of(dao.punchesFor(date.toEpochDay()).map { it.toPunch() })
            val why = reason?.trim()?.takeIf { it.isNotEmpty() }
            if (after != before || why != null) {
                dao.insertEdit(EditEntity(epochDay = date.toEpochDay(), atMillis = System.currentTimeMillis(), change = after.describeChange(before) { it.formatClock() }, reason = why))
            }
        }
    }

    /** Replaces [date]'s entries with [shape] in one go. */
    fun saveDay(date: LocalDate, shape: DayShape, reason: String?) = logged(date, reason) {
        dao.deletePunchesFor(date.toEpochDay())
        dao.insertAll(
            shape.toPunches().map { PunchEntity(epochDay = date.toEpochDay(), secondOfDay = it.time.toSecondOfDay(), isIn = it.isIn, isLogout = it.isLogout, label = it.label) }
        )
    }

    /** Runs a change, then refreshes the widget and reminders. */
    private fun change(block: suspend () -> Unit) = viewModelScope.launch {
        block()
        Tracker.onChanged(getApplication())
    }

    fun updateSettings(settings: Settings) = change {
        SettingsStore.save(getApplication(), settings)
    }

    fun setDayType(date: LocalDate, type: DayType) = change {
        val existing = dao.day(date.toEpochDay())
        dao.upsertDay(existing?.copy(type = type.name) ?: DayEntity(date.toEpochDay(), type.name))
    }

    /** Marks an LOP day as regularized with the manager, or undoes it ([note] null). */
    fun setRegularized(date: LocalDate, currentType: DayType, note: String?) = change {
        val existing = dao.day(date.toEpochDay()) ?: DayEntity(date.toEpochDay(), currentType.name)
        dao.upsertDay(
            existing.copy(
                regularized = note != null,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                regularizedOn = if (note != null) LocalDate.now().toEpochDay() else null,
            )
        )
    }

    /** Deletes every entry and day setting. */
    /** Keeps a copy first so [undoClear] can bring everything back. */
    fun clearAll() = change {
        Backup.saveBeforeClear(getApplication())
        db.withTransaction {
            dao.deleteAllPunches()
            dao.deleteAllDays()
            dao.deleteAllEdits()
        }
    }

    fun undoClear(onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(
            runCatching { Backup.undoClear(getApplication()) }
                .fold({ "Restored $it records" }, { "Undo failed: ${it.message}" })
        )
    }

    /** Turns the weekly backup on for [folder] and writes the first one now. */
    fun setBackupFolder(folder: Uri?) = change {
        SettingsStore.save(getApplication(), settings.value.copy(backupFolder = folder?.toString()))
        if (folder != null) Backup.resetAutoBackup(getApplication())
    }

    /** Records a login/break end (isIn), break start or logout right now. */
    fun punchNow(isIn: Boolean, isLogout: Boolean = false) = viewModelScope.launch {
        Tracker.punchNow(getApplication(), isIn, isLogout)
    }

    /** Puts a limit of [minutes] on the break in progress; null removes it. */
    fun setBreakLimit(minutes: Int?) = viewModelScope.launch {
        Tracker.setBreakLimit(getApplication(), minutes?.let { Duration.ofMinutes(it.toLong()) })
    }

    /** Adds the Settings extend time to the break's limit. */
    fun extendBreak() = viewModelScope.launch { Tracker.extendBreak(getApplication()) }

    /** Changes what the break starting at [start] was for. */
    fun setBreakLabel(start: PunchEntity, label: BreakLabel) = logged(start.date) {
        dao.update(start.copy(label = label.name))
    }

    /** Saves a whole break at once: its label, start and (if it has ended) end. */
    fun saveBreak(start: PunchEntity, end: PunchEntity?, label: BreakLabel, from: LocalTime, to: LocalTime?) = logged(start.date) {
        dao.update(start.copy(secondOfDay = from.toSecondOfDay(), label = label.name))
        if (end != null && to != null) dao.update(end.copy(secondOfDay = to.toSecondOfDay()))
    }

    /** Removes a break: its start, and its end if it has one. */
    fun deleteBreak(start: PunchEntity, end: PunchEntity?) = logged(start.date) {
        dao.delete(start)
        end?.let { dao.delete(it) }
    }

    /** The plan for the break [s] is on, if any. */
    fun breakPlan(s: DaySummary): BreakPlan? = BreakPlans.current(getApplication(), s)

    /** Adds an entry; [label] applies only to a break start. */
    fun add(date: LocalDate, time: LocalTime, isIn: Boolean, isLogout: Boolean, label: BreakLabel? = null) = logged(date) {
        val breakStart = !isIn && !isLogout
        dao.insert(
            PunchEntity(
                epochDay = date.toEpochDay(),
                secondOfDay = time.toSecondOfDay(),
                isIn = isIn,
                isLogout = !isIn && isLogout,
                label = if (breakStart) (label ?: BreakLabel.guess(time)).name else null,
            )
        )
    }

    fun update(punch: PunchEntity, time: LocalTime, isIn: Boolean, isLogout: Boolean, label: BreakLabel? = null) = logged(punch.date) {
        val breakStart = !isIn && !isLogout
        dao.update(
            punch.copy(
                secondOfDay = time.toSecondOfDay(),
                isIn = isIn,
                isLogout = !isIn && isLogout,
                label = if (breakStart) (label ?: BreakLabel.of(punch.label) ?: BreakLabel.guess(time)).name else null,
            )
        )
    }

    /** Turns the break you're on into the end of the day. */
    fun convertToLogout(punch: PunchEntity) = logged(punch.date) {
        dao.update(punch.copy(isLogout = true))
    }

    fun delete(punch: PunchEntity) = logged(punch.date) {
        dao.delete(punch)
    }

    fun export(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(
            runCatching { Backup.export(getApplication(), uri) }
                .fold({ "Backup saved ($it records)" }, { "Backup failed: ${it.message}" })
        )
    }

    fun import(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(
            runCatching { Backup.import(getApplication(), uri) }
                .fold({ "Restored $it records" }, { "Restore failed: ${it.message}" })
        )
    }

    /** Refreshes the widget and reminders, e.g. when the app comes to the foreground. */
    fun refresh() = viewModelScope.launch { Tracker.onChanged(getApplication()) }
}
