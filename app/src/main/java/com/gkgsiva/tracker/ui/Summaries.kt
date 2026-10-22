package com.gkgsiva.tracker.ui

import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DayCalculator
import com.gkgsiva.tracker.domain.DaySummary
import java.time.LocalDate
import java.time.LocalDateTime

fun summarizeDay(date: LocalDate, punches: List<PunchEntity>, now: LocalDateTime): DaySummary =
    DayCalculator.summarize(
        date = date,
        punches = punches.map { it.toPunch() },
        now = if (date == now.toLocalDate()) now.toLocalTime() else null,
    )

fun summarizeAll(punchesByDay: Map<LocalDate, List<PunchEntity>>, now: LocalDateTime): Map<LocalDate, DaySummary> =
    punchesByDay.mapValues { (date, punches) -> summarizeDay(date, punches, now) }
