package com.gkgsiva.tracker.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** How long you meant a break to be, set when it starts. */
data class BreakPlan(val date: LocalDate, val start: LocalTime, val length: Duration) {
    val end: LocalTime get() = start.plus(length)

    /** True only for the break [s] is on right now; a plan never carries over to the next break. */
    fun appliesTo(s: DaySummary): Boolean = s.status == DayStatus.ON_BREAK && s.date == date && s.lastOut == start

    /** Time left on the plan at [now]; negative once it has run over. */
    fun left(now: LocalTime): Duration = Duration.between(now, end)

    companion object {
        /** Lengths offered when starting a break, in minutes. */
        val CHOICES = listOf(5, 10, 15, 20, 30, 45, 60)
    }
}
