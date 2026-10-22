package com.gkgsiva.tracker.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Totals for a week or a month. */
data class PeriodSummary(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val daysWorked: Int,
    val totalInside: Duration,
    val totalBreaks: Duration,
    val lateDays: Int,
    val shortDays: Int,
) {
    val avgInside: Duration get() = if (daysWorked == 0) Duration.ZERO else totalInside.dividedBy(daysWorked.toLong())
    val avgBreaks: Duration get() = if (daysWorked == 0) Duration.ZERO else totalBreaks.dividedBy(daysWorked.toLong())
}

object Periods {

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The last [count] weeks (Monday to Sunday), oldest first, ending with the week containing [today]. */
    fun weekly(days: Map<LocalDate, DaySummary>, today: LocalDate, count: Int): List<PeriodSummary> {
        val current = weekStart(today)
        return (count - 1 downTo 0).map { i ->
            val start = current.minusWeeks(i.toLong())
            summarize(days, start, start.plusDays(6))
        }
    }

    /** The last [count] calendar months, oldest first, ending with the month containing [today]. */
    fun monthly(days: Map<LocalDate, DaySummary>, today: LocalDate, count: Int): List<PeriodSummary> {
        val current = YearMonth.from(today)
        return (count - 1 downTo 0).map { i ->
            val month = current.minusMonths(i.toLong())
            summarize(days, month.atDay(1), month.atEndOfMonth())
        }
    }

    fun summarize(days: Map<LocalDate, DaySummary>, start: LocalDate, endInclusive: LocalDate): PeriodSummary {
        val inRange = days.values.filter { it.worked && it.date >= start && it.date <= endInclusive }
        return PeriodSummary(
            start = start,
            endInclusive = endInclusive,
            daysWorked = inRange.size,
            totalInside = inRange.fold(Duration.ZERO) { acc, d -> acc + d.inside },
            totalBreaks = inRange.fold(Duration.ZERO) { acc, d -> acc + d.breaks },
            lateDays = inRange.count { it.lateArrival },
            shortDays = inRange.count { !it.targetMet && it.status != DayStatus.INSIDE && it.status != DayStatus.ON_BREAK },
        )
    }
}
