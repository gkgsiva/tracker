package com.gkgsiva.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.gkgsiva.tracker.data.AppDatabase
import com.gkgsiva.tracker.data.DayEntity
import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DayType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TrackerViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.punchDao()

    /** All punches grouped by day. A personal tracker stays small enough to keep in memory. */
    val punchesByDay: StateFlow<Map<LocalDate, List<PunchEntity>>> = dao.observeAll()
        .map { all -> all.groupBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Days that aren't plain office days. */
    val dayTypes: StateFlow<Map<LocalDate, DayType>> = dao.observeDays()
        .map { days ->
            days.mapNotNull { d ->
                val type = DayType.entries.firstOrNull { it.name == d.type } ?: return@mapNotNull null
                if (type == DayType.OFFICE) null else LocalDate.ofEpochDay(d.epochDay) to type
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setDayType(date: LocalDate, type: DayType) = viewModelScope.launch {
        dao.upsertDay(DayEntity(date.toEpochDay(), type.name))
    }

    /** Deletes every entry and day setting. */
    fun clearAll() = viewModelScope.launch {
        db.withTransaction {
            dao.deleteAllPunches()
            dao.deleteAllDays()
        }
    }

    /** Records a login/break end (isIn), break start or logout right now. */
    fun punchNow(isIn: Boolean, isLogout: Boolean = false) {
        val now = LocalDateTime.now().withNano(0)
        add(now.toLocalDate(), now.toLocalTime(), isIn, isLogout)
    }

    fun add(date: LocalDate, time: LocalTime, isIn: Boolean, isLogout: Boolean) = viewModelScope.launch {
        dao.insert(
            PunchEntity(
                epochDay = date.toEpochDay(),
                secondOfDay = time.toSecondOfDay(),
                isIn = isIn,
                isLogout = !isIn && isLogout,
            )
        )
    }

    fun update(punch: PunchEntity, time: LocalTime, isIn: Boolean, isLogout: Boolean) = viewModelScope.launch {
        dao.update(punch.copy(secondOfDay = time.toSecondOfDay(), isIn = isIn, isLogout = !isIn && isLogout))
    }

    /** Turns the break you're on into the end of the day. */
    fun convertToLogout(punch: PunchEntity) = viewModelScope.launch {
        dao.update(punch.copy(isLogout = true))
    }

    fun delete(punch: PunchEntity) = viewModelScope.launch {
        dao.delete(punch)
    }
}
