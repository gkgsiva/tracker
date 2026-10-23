package com.gkgsiva.tracker.ui

import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DayCalculator
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
import java.time.LocalDate
import java.time.LocalDateTime

fun summarizeDay(date: LocalDate, punches: List<PunchEntity>, now: LocalDateTime, type: DayType): DaySummary =
    DayCalculator.summarize(
        date = date,
        punches = punches.map { it.toPunch() },
        now = if (date == now.toLocalDate()) now.toLocalTime() else null,
        type = type,
    )

fun summarizeAll(
    punchesByDay: Map<LocalDate, List<PunchEntity>>,
    dayTypes: Map<LocalDate, DayType>,
    now: LocalDateTime,
): Map<LocalDate, DaySummary> =
    (punchesByDay.keys + dayTypes.keys).associateWith { date ->
        summarizeDay(date, punchesByDay[date].orEmpty(), now, dayTypes[date] ?: DayType.OFFICE)
    }
