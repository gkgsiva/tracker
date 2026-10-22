package com.gkgsiva.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gkgsiva.tracker.data.AppDatabase
import com.gkgsiva.tracker.data.PunchEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TrackerViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).punchDao()

    /** All punches grouped by day. A personal tracker stays small enough to keep in memory. */
    val punchesByDay: StateFlow<Map<LocalDate, List<PunchEntity>>> = dao.observeAll()
        .map { all -> all.groupBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Records a check-in or check-out right now. */
    fun punchNow(isIn: Boolean) {
        val now = LocalDateTime.now().withNano(0)
        add(now.toLocalDate(), now.toLocalTime(), isIn)
    }

    fun add(date: LocalDate, time: LocalTime, isIn: Boolean) = viewModelScope.launch {
        dao.insert(PunchEntity(epochDay = date.toEpochDay(), secondOfDay = time.toSecondOfDay(), isIn = isIn))
    }

    fun update(punch: PunchEntity, time: LocalTime, isIn: Boolean) = viewModelScope.launch {
        dao.update(punch.copy(secondOfDay = time.toSecondOfDay(), isIn = isIn))
    }

    fun delete(punch: PunchEntity) = viewModelScope.launch {
        dao.delete(punch)
    }
}
