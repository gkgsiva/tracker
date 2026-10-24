package com.gkgsiva.tracker.ui

import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DayCalculator
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
import com.gkgsiva.tracker.domain.Settings
import java.time.LocalDate
import java.time.LocalDateTime

fun summarizeDay(
    date: LocalDate,
    punches: List<PunchEntity>,
    now: LocalDateTime,
    explicitType: DayType?,
    settings: Settings,
    regularized: String? = null,
): DaySummary =
    DayCalculator.summarize(
        date = date,
        punches = punches.map { it.toPunch() },
        now = if (date == now.toLocalDate()) now.toLocalTime() else null,
        rules = settings.rules,
        type = DayType.effective(explicitType, date, punches.isNotEmpty(), settings.weeklyOffs),
        regularized = regularized,
    )

/** Summaries for every day that has entries or a chosen type. */
fun summarizeAll(
    punchesByDay: Map<LocalDate, List<PunchEntity>>,
    dayTypes: Map<LocalDate, DayType>,
    now: LocalDateTime,
    settings: Settings,
    regularized: Map<LocalDate, String> = emptyMap(),
): Map<LocalDate, DaySummary> =
    (punchesByDay.keys + dayTypes.keys).associateWith { date ->
        summarizeDay(date, punchesByDay[date].orEmpty(), now, dayTypes[date], settings, regularized[date])
    }
