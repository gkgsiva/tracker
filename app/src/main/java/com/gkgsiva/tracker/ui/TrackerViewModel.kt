package com.gkgsiva.tracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.gkgsiva.tracker.data.AppDatabase
import com.gkgsiva.tracker.data.DayEntity
import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DayType
import com.gkgsiva.tracker.domain.Settings
import com.gkgsiva.tracker.system.Backup
import com.gkgsiva.tracker.system.SettingsStore
import com.gkgsiva.tracker.system.Tracker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    val settings: StateFlow<Settings> = SettingsStore.flow(app)

    /** Runs a change, then refreshes the widget and reminders. */
    private fun change(block: suspend () -> Unit) = viewModelScope.launch {
        block()
        Tracker.onChanged(getApplication())
    }

    fun updateSettings(settings: Settings) = change { SettingsStore.save(getApplication(), settings) }

    fun setDayType(date: LocalDate, type: DayType) = change {
        dao.upsertDay(DayEntity(date.toEpochDay(), type.name))
    }

    /** Deletes every entry and day setting. */
    fun clearAll() = change {
        db.withTransaction {
            dao.deleteAllPunches()
            dao.deleteAllDays()
        }
    }

    /** Records a login/break end (isIn), break start or logout right now. */
    fun punchNow(isIn: Boolean, isLogout: Boolean = false) = viewModelScope.launch {
        Tracker.punchNow(getApplication(), isIn, isLogout)
    }

    fun add(date: LocalDate, time: LocalTime, isIn: Boolean, isLogout: Boolean) = change {
        dao.insert(
            PunchEntity(
                epochDay = date.toEpochDay(),
                secondOfDay = time.toSecondOfDay(),
                isIn = isIn,
                isLogout = !isIn && isLogout,
            )
        )
    }

    fun update(punch: PunchEntity, time: LocalTime, isIn: Boolean, isLogout: Boolean) = change {
        dao.update(punch.copy(secondOfDay = time.toSecondOfDay(), isIn = isIn, isLogout = !isIn && isLogout))
    }

    /** Turns the break you're on into the end of the day. */
    fun convertToLogout(punch: PunchEntity) = change {
        dao.update(punch.copy(isLogout = true))
    }

    fun delete(punch: PunchEntity) = change {
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
