package com.gkgsiva.tracker.ui

import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
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
    /** Required time in the office for each working day, breaks included. */
    val perDayPresent: Duration = perDay,
) {
    val daysFinished: Int get() = daysMet + daysShort

    /** e.g. "8h work, 9h in office", or just "8h" when there's no separate office time. */
    val hoursText: String get() = hoursText(perDay, perDayPresent)

    companion object {
        fun hoursText(work: Duration, present: Duration): String =
            if (present > work) "${work.formatShort()} work, ${present.formatShort()} in office" else work.formatShort()

        fun hoursOf(rules: Rules): String = hoursText(rules.requiredInside, rules.requiredPresent)
    }
}

/** One-line summary, e.g. "5 working days left · 8h each · 1 day short". */
fun Outlook.paceText(): String = buildList {
    add(
        if (daysLeft > 0) "$daysLeft working day${if (daysLeft == 1) "" else "s"} left · $hoursText each"
        else "No working days left"
    )
    if (daysShort > 0) add("$daysShort day${if (daysShort == 1) "" else "s"} short of $hoursText")
}.joinToString(" · ")

/** e.g. "3 of 4 days met 8h"; null before any working day has finished. */
fun Outlook.metText(): String? =
    if (daysFinished == 0) null else "$daysMet of $daysFinished day${if (daysFinished == 1) "" else "s"} met $hoursText"

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
        val met = finished.count { it.targetMet }
        return Outlook(
            start = start,
            endInclusive = endInclusive,
            perDay = rules.requiredInside,
            perDayPresent = rules.requiredPresent,
            done = counted.fold(Duration.ZERO) { acc, d -> acc + d.inside },
            daysMet = met,
            daysShort = finished.size - met,
            daysLeft = counted.size - finished.size,
            lopDays = days.count { it.lop },
        )
    }

    /** Which rule sets the leave time, e.g. "9h in office from 8:18 am". */
    fun leaveRule(s: DaySummary): String {
        val rules = s.rules
        val leave = s.expectedLeave ?: return ""
        val firstIn = s.firstIn ?: return ""
        val presentAt = firstIn.plus(rules.requiredPresent)
        return when {
            leave == rules.leaveAfter && presentAt < leave -> "core hours end at ${rules.leaveAfter.formatClock()}"
            rules.requiredPresent > rules.requiredInside && leave == presentAt ->
                "${rules.requiredPresent.formatShort()} in office from ${firstIn.formatClock()}"
            else -> "${rules.requiredInside.formatShort()} of work, breaks excluded"
        }
    }

    /** Today's LOP outlook; null on days off. */
    fun forecast(s: DaySummary, now: LocalTime): Forecast? {
        val rules = s.rules
        return when {
            s.dayOff -> null
            s.status == DayStatus.NOT_STARTED && s.type == DayType.HALF_DAY ->
                Forecast(false, "Half day: ${rules.requiredInside.formatShort()} inside at any time")
            s.status == DayStatus.NOT_STARTED -> if (now < rules.arriveBy) {
                Forecast(false, "Log in by ${rules.arriveBy.formatClock()} (${java.time.Duration.between(now, rules.arriveBy).formatHm()} left) to avoid LOP")
            } else {
                Forecast(true, "Heading for LOP: no login before ${rules.arriveBy.formatClock()}")
            }
            s.lop -> Forecast(true, "LOP today: " + s.lopReasons.joinToString("; ") { it.describe(s) })
            s.isRegularized -> Forecast(false, "Regularized: " + s.lopReasons.joinToString { it.shortLabel() })
            s.status == DayStatus.INSIDE -> Forecast(false, "On track: you can leave at ${s.expectedLeave?.formatClock()} (${leaveRule(s)})")
            s.status == DayStatus.ON_BREAK ->
                Forecast(false, "On track if you're back now: stay until ${s.expectedLeave?.formatClock()}")
            else -> Forecast(false, "Day complete, no LOP")
        }
    }
}
