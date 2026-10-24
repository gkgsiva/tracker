package com.gkgsiva.tracker.ui

import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.Rules
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * A week or month at a glance. Hours don't pool: every working day needs the full required hours
 * on its own, so this counts days that met it rather than a total to catch up on.
 */
data class Outlook(
    val start: LocalDate,
    val endInclusive: LocalDate,
    /** Required inside time for each working day. */
    val perDay: Duration,
    /** Time clocked inside so far, as a record. */
    val done: Duration,
    /** Finished working days that reached [perDay]. */
    val daysMet: Int,
    /** Finished working days that fell short of [perDay]. */
    val daysShort: Int,
    /** Working days left, today included while it's still running. */
    val daysLeft: Int,
    val lopDays: Int,
) {
    val daysFinished: Int get() = daysMet + daysShort
}

/** One-line summary, e.g. "5 working days left · 8h each · 1 day short". */
fun Outlook.paceText(): String = buildList {
    add(
        if (daysLeft > 0) "$daysLeft working day${if (daysLeft == 1) "" else "s"} left · ${perDay.formatShort()} each"
        else "No working days left"
    )
    if (daysShort > 0) add("$daysShort day${if (daysShort == 1) "" else "s"} short of ${perDay.formatShort()}")
}.joinToString(" · ")

/** e.g. "3 of 4 days met 8h"; null before any working day has finished. */
fun Outlook.metText(): String? =
    if (daysFinished == 0) null else "$daysMet of $daysFinished day${if (daysFinished == 1) "" else "s"} met ${perDay.formatShort()}"

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
        val finished = counted.filter { it.date < today || (it.date == today && it.finished) }
        val met = finished.count { it.inside >= rules.requiredInside }
        return Outlook(
            start = start,
            endInclusive = endInclusive,
            perDay = rules.requiredInside,
            done = counted.fold(Duration.ZERO) { acc, d -> acc + d.inside },
            daysMet = met,
            daysShort = finished.size - met,
            daysLeft = counted.size - finished.size,
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
