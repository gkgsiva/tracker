package com.gkgsiva.tracker.ui

import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.Rules
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Hours against target for a week or month, with what's still needed. */
data class Outlook(
    val start: LocalDate,
    val endInclusive: LocalDate,
    /** Required hours × working days (planned leave, holidays and weekly offs excluded). */
    val target: Duration,
    val done: Duration,
    /** Done minus required hours for the working days already finished: positive is ahead. */
    val balance: Duration,
    /** Working days left, today included while it's still running. */
    val daysLeft: Int,
    /** Average needed per remaining working day to reach [target]; null when no days are left. */
    val neededPerDay: Duration?,
    val lopDays: Int,
) {
    val remaining: Duration get() = (target - done).coerceAtLeast(Duration.ZERO)
}

/** Whether today is heading for LOP, and why. */
data class Forecast(val atRisk: Boolean, val message: String)

object Insights {

    /**
     * Past working days with nothing logged are left out, so days before you started using the
     * app (or genuinely missed days) don't distort the target.
     */
    fun outlook(
        start: LocalDate,
        endInclusive: LocalDate,
        today: LocalDate,
        rules: Rules,
        summaryOf: (LocalDate) -> DaySummary,
    ): Outlook {
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= endInclusive }.map(summaryOf).toList()
        val counted = days.filter { !it.dayOff && (it.date >= today || it.worked) }
        val completed = counted.filter { it.date < today || (it.date == today && it.finished) }
        val left = counted.size - completed.size
        val target = rules.requiredInside.multipliedBy(counted.size.toLong())
        val done = counted.fold(Duration.ZERO) { acc, d -> acc + d.inside }
        val doneCompleted = completed.fold(Duration.ZERO) { acc, d -> acc + d.inside }
        val remaining = (target - done).coerceAtLeast(Duration.ZERO)
        return Outlook(
            start = start,
            endInclusive = endInclusive,
            target = target,
            done = done,
            balance = doneCompleted - rules.requiredInside.multipliedBy(completed.size.toLong()),
            daysLeft = left,
            neededPerDay = if (left > 0) remaining.dividedBy(left.toLong()) else null,
            lopDays = days.count { it.lop },
        )
    }

    /** Today's LOP outlook; null on days off. */
    fun forecast(s: DaySummary, now: LocalTime): Forecast? {
        val rules = s.rules
        return when {
            s.dayOff -> null
            s.status == DayStatus.NOT_STARTED -> if (now < rules.arriveBy) {
                Forecast(false, "Log in by ${rules.arriveBy.formatClock()} (${java.time.Duration.between(now, rules.arriveBy).formatHm()} left) to avoid LOP")
            } else {
                Forecast(true, "Heading for LOP: no login before ${rules.arriveBy.formatClock()}")
            }
            s.lop -> Forecast(true, "LOP today: " + s.lopReasons.joinToString { it.shortLabel() })
            s.status == DayStatus.INSIDE -> Forecast(false, "On track: stay until ${s.expectedLeave?.formatClock()}")
            s.status == DayStatus.ON_BREAK ->
                Forecast(false, "On track if you're back now: stay until ${s.expectedLeave?.formatClock()}")
            else -> Forecast(false, "Day complete, no LOP")
        }
    }
}
