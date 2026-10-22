package com.gkgsiva.tracker.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * A single check-in (isIn = true) or check-out on some day. A check-out with [isLogout] ends the
 * day; any other check-out starts a break.
 */
data class Punch(val time: LocalTime, val isIn: Boolean, val isLogout: Boolean = false)

enum class DayStatus { NOT_STARTED, INSIDE, ON_BREAK, DONE, MISSING_CHECKOUT }

/** Rules whose breach makes the day loss of pay, regardless of hours worked. */
enum class LopReason { LATE_LOGIN, EARLY_EXIT, UNDER_8H, MISSING_CHECKOUT }

data class DaySummary(
    val date: LocalDate,
    val status: DayStatus,
    val firstIn: LocalTime?,
    val lastOut: LocalTime?,
    val inside: Duration,
    val breaks: Duration,
    val breakCount: Int,
    val remaining: Duration,
    /** Break time left in the 1h guideline; negative when over. A longer break isn't LOP by itself. */
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
    val finished: Boolean get() = status == DayStatus.DONE || status == DayStatus.MISSING_CHECKOUT

    /**
     * Any single breach makes the day LOP, even with 8h+ inside. A late login is known as soon
     * as it happens; an early exit or short hours only once the day is finished.
     */
    val lopReasons: List<LopReason>
        get() = buildList {
            if (lateArrival) add(LopReason.LATE_LOGIN)
            if (earlyLeave) add(LopReason.EARLY_EXIT)
            if (status == DayStatus.MISSING_CHECKOUT) add(LopReason.MISSING_CHECKOUT)
            else if (finished && !targetMet) add(LopReason.UNDER_8H)
        }
    val lop: Boolean get() = lopReasons.isNotEmpty()
}

object DayCalculator {

    /**
     * Summarizes one day's punches. Pass [now] only for the current day so that an open
     * check-in or an ongoing break keeps counting live.
     *
     * A day starts with the first check-in. Each check-out followed by a check-in is a break.
     * On today, a trailing break check-out is an ongoing break and a logout ends the day. On past
     * days any trailing check-out counts as leaving.
     */
    fun summarize(date: LocalDate, punches: List<Punch>, now: LocalTime?, rules: Rules = Rules()): DaySummary {
        var inside = Duration.ZERO
        var breaks = Duration.ZERO
        var breakCount = 0
        var firstIn: LocalTime? = null
        var lastOut: LocalTime? = null
        var inSince: LocalTime? = null
        var outSince: LocalTime? = null
        var loggedOut = false

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
                loggedOut = p.isLogout
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
            now != null && outSince != null && !loggedOut -> {
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
