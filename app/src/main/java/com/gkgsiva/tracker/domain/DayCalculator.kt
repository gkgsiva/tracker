package com.gkgsiva.tracker.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** A single check-in (isIn = true) or check-out on some day. */
data class Punch(val time: LocalTime, val isIn: Boolean)

enum class DayStatus { NOT_STARTED, INSIDE, ON_BREAK, DONE, MISSING_CHECKOUT }

data class DaySummary(
    val date: LocalDate,
    val status: DayStatus,
    val firstIn: LocalTime?,
    val lastOut: LocalTime?,
    val inside: Duration,
    val breaks: Duration,
    val breakCount: Int,
    val remaining: Duration,
    /** Break time still available; negative when the allowance is exceeded. */
    val breakLeft: Duration,
    /** Earliest time you can leave and satisfy both the 8h and 5 PM rules (live days only). */
    val expectedLeave: LocalTime?,
    /** Latest time to come back from the current break without exceeding the allowance. */
    val returnBy: LocalTime?,
    val rules: Rules,
) {
    val worked: Boolean get() = firstIn != null
    val targetMet: Boolean get() = inside >= rules.requiredInside
    val lateArrival: Boolean get() = firstIn != null && firstIn > rules.arriveBy
    val earlyLeave: Boolean get() = status == DayStatus.DONE && lastOut != null && lastOut < rules.leaveAfter
    val breakExceeded: Boolean get() = breakLeft.isNegative
}

object DayCalculator {

    /**
     * Summarizes one day's punches. Pass [now] only for the current day so that an open
     * check-in or an ongoing break keeps counting live.
     *
     * A day starts with the first check-in. Each check-out followed by a check-in is a break.
     * A trailing check-out on today is treated as an ongoing break until the 8h target is met,
     * after which it's treated as leaving for the day.
     */
    fun summarize(date: LocalDate, punches: List<Punch>, now: LocalTime?, rules: Rules = Rules()): DaySummary {
        var inside = Duration.ZERO
        var breaks = Duration.ZERO
        var breakCount = 0
        var firstIn: LocalTime? = null
        var lastOut: LocalTime? = null
        var inSince: LocalTime? = null
        var outSince: LocalTime? = null

        for (p in punches.sortedBy { it.time }) {
            if (p.isIn) {
                if (inSince != null) continue
                if (firstIn == null) {
                    firstIn = p.time
                } else if (outSince != null) {
                    breaks += Duration.between(outSince, p.time)
                    breakCount++
                }
                inSince = p.time
                outSince = null
            } else {
                if (inSince == null) continue
                inside += Duration.between(inSince, p.time)
                lastOut = p.time
                inSince = null
                outSince = p.time
            }
        }

        var returnBy: LocalTime? = null
        val status = when {
            firstIn == null -> DayStatus.NOT_STARTED
            inSince != null && now == null -> DayStatus.MISSING_CHECKOUT
            inSince != null -> {
                if (now!! > inSince) inside += Duration.between(inSince, now)
                DayStatus.INSIDE
            }
            now != null && outSince != null && inside < rules.requiredInside -> {
                if (now > outSince) breaks += Duration.between(outSince, now)
                breakCount++
                val left = rules.breakAllowance - breaks
                if (!left.isNegative) returnBy = now.plus(left)
                DayStatus.ON_BREAK
            }
            else -> DayStatus.DONE
        }

        val remaining = (rules.requiredInside - inside).coerceAtLeast(Duration.ZERO)
        val expectedLeave = if (now != null && (status == DayStatus.INSIDE || status == DayStatus.ON_BREAK)) {
            maxOf(now.plus(remaining), rules.leaveAfter)
        } else null

        return DaySummary(
            date = date,
            status = status,
            firstIn = firstIn,
            lastOut = lastOut,
            inside = inside,
            breaks = breaks,
            breakCount = breakCount,
            remaining = remaining,
            breakLeft = rules.breakAllowance - breaks,
            expectedLeave = expectedLeave,
            returnBy = returnBy,
            rules = rules,
        )
    }
}
