package com.gkgsiva.tracker.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * A single check-in (isIn = true) or check-out on some day. A check-out with [isLogout] ends the
 * day; any other check-out starts a break.
 */
data class Punch(val time: LocalTime, val isIn: Boolean, val isLogout: Boolean = false)

enum class DayStatus { NOT_STARTED, INSIDE, ON_BREAK, DONE, MISSING_CHECKOUT, DAY_OFF }

enum class DayType(val label: String) {
    OFFICE("Office"),
    /** Only first login and last logout count, no breaks; same LOP rules. */
    WFH("WFH"),
    HOLIDAY("Holiday"),
    LEAVE("On leave"),
    /** In the office for [Rules.halfDayHours], at any time; see [Rules.forType]. */
    HALF_DAY("Half day"),
    /** A few hours off at the start or end; see [Rules.forType]. */
    SHORT_LEAVE("Short leave"),
    /** Not chosen by hand: a weekly off day (e.g. Sunday) with nothing logged. */
    WEEKLY_OFF("Weekly off");

    /** No login needed and never LOP. */
    val isDayOff: Boolean get() = this == HOLIDAY || this == LEAVE || this == WEEKLY_OFF

    /** A working day with fewer hours; see [Rules.forType]. */
    val isPartDay: Boolean get() = this == HALF_DAY || this == SHORT_LEAVE

    /** Types the user picks for a day. */
    val choosable: Boolean get() = this != WEEKLY_OFF

    companion object {
        /**
         * The type a day is treated as: an explicit choice wins; otherwise a weekly off day counts
         * as off unless something was logged on it.
         */
        fun effective(explicit: DayType?, date: LocalDate, hasPunches: Boolean, weeklyOffs: Set<DayOfWeek>): DayType =
            explicit ?: if (!hasPunches && date.dayOfWeek in weeklyOffs) WEEKLY_OFF else OFFICE
    }
}

/** Entries that don't make sense together; the calculation skips them, but they should be fixed. */
enum class EntryIssueKind { OUT_BEFORE_LOGIN, DOUBLE_IN, DOUBLE_OUT, OPEN_BREAK }

data class EntryIssue(val kind: EntryIssueKind, val time: LocalTime)

/** Rules whose breach makes the day loss of pay, regardless of hours worked. */
enum class LopReason {
    LATE_LOGIN,
    EARLY_EXIT,
    /** Less than [Rules.requiredInside] of work, breaks excluded. */
    SHORT_WORK,
    /** Less than [Rules.requiredPresent] from login to logout, breaks included. */
    SHORT_PRESENT,
    MISSING_CHECKOUT,
}

data class DaySummary(
    val date: LocalDate,
    val status: DayStatus,
    val firstIn: LocalTime?,
    val lastOut: LocalTime?,
    /** Work time, breaks excluded. */
    val inside: Duration,
    val breaks: Duration,
    val breakCount: Int,
    /** Time until both the work and in-office hours are reached, if you keep working. */
    val remaining: Duration,
    /** Break that still lets you leave at the earliest the rules allow; see [Rules.breakAllowance]. */
    val breakAllowance: Duration,
    /** Break time left in [breakAllowance]; negative when over. A longer break isn't LOP by itself. */
    val breakLeft: Duration,
    /** Earliest time you can leave meeting every rule: work hours, in-office hours, leave-after (live days only). */
    val expectedLeave: LocalTime?,
    /** Latest time to come back from the current break without exceeding the allowance. */
    val returnBy: LocalTime?,
    val rules: Rules,
    val type: DayType = DayType.OFFICE,
    /** Set when the manager regularized this day's LOP: the note, or "" without one. */
    val regularized: String? = null,
    val issues: List<EntryIssue> = emptyList(),
    /** From first login to logout (or now), breaks included. */
    val present: Duration = Duration.ZERO,
) {
    val wfh: Boolean get() = type == DayType.WFH
    val dayOff: Boolean get() = type.isDayOff
    val worked: Boolean get() = firstIn != null
    val workMet: Boolean get() = inside >= rules.requiredInside
    val presentMet: Boolean get() = present >= rules.requiredPresent
    /** Both the work hours and the in-office hours are done. */
    val targetMet: Boolean get() = workMet && presentMet
    val lateArrival: Boolean get() = firstIn != null && firstIn > rules.arriveBy
    val earlyLeave: Boolean get() = status == DayStatus.DONE && lastOut != null && lastOut < rules.leaveAfter
    val breakExceeded: Boolean get() = breakLeft.isNegative
    val finished: Boolean get() = status == DayStatus.DONE || status == DayStatus.MISSING_CHECKOUT

    /**
     * Any single breach makes the day LOP, even with extra hours. A late login is known as soon
     * as it happens; an early exit or short hours only once the day is finished.
     */
    val lopReasons: List<LopReason>
        get() = buildList {
            if (lateArrival) add(LopReason.LATE_LOGIN)
            if (earlyLeave) add(LopReason.EARLY_EXIT)
            if (status == DayStatus.MISSING_CHECKOUT) add(LopReason.MISSING_CHECKOUT)
            else if (finished) {
                if (!workMet) add(LopReason.SHORT_WORK)
                // Without a separate office time (WFH, half day) this would only repeat SHORT_WORK.
                if (!presentMet && rules.requiredPresent > rules.requiredInside) add(LopReason.SHORT_PRESENT)
            }
        }
    /** LOP unless it was regularized; [lopReasons] still says what happened. */
    val lop: Boolean get() = lopReasons.isNotEmpty() && regularized == null
    val isRegularized: Boolean get() = lopReasons.isNotEmpty() && regularized != null
}

object DayCalculator {

    /**
     * Summarizes one day's punches. Pass [now] only for the current day so that an open
     * check-in or an ongoing break keeps counting live.
     *
     * A day starts with the first check-in. Each check-out followed by a check-in is a break.
     * On today, a trailing break check-out is an ongoing break and a logout ends the day. On past
     * days any trailing check-out counts as leaving.
     *
     * On a WFH day only the first login and the last check-out are used, so there are no breaks.
     * Holidays and leave days ignore punches entirely.
     */
    fun summarize(
        date: LocalDate,
        punches: List<Punch>,
        now: LocalTime?,
        rules: Rules = Rules(),
        type: DayType = DayType.OFFICE,
        regularized: String? = null,
    ): DaySummary {
        val rules = rules.forType(type)
        var inside = Duration.ZERO
        var breaks = Duration.ZERO
        var breakCount = 0
        var firstIn: LocalTime? = null
        var lastOut: LocalTime? = null
        var inSince: LocalTime? = null
        var outSince: LocalTime? = null
        var loggedOut = false

        val effective = when {
            type.isDayOff -> emptyList()
            type == DayType.WFH -> wfhPunches(punches)
            else -> punches.sortedBy { it.time }
        }
        val issues = mutableListOf<EntryIssue>()
        for (p in effective) {
            if (p.isIn) {
                if (inSince != null) {
                    issues += EntryIssue(EntryIssueKind.DOUBLE_IN, p.time)
                    continue
                }
                if (firstIn == null) {
                    firstIn = p.time
                } else if (outSince != null) {
                    breaks += Duration.between(outSince, p.time)
                    breakCount++
                }
                inSince = p.time
                outSince = null
            } else {
                if (inSince == null) {
                    issues += EntryIssue(if (firstIn == null) EntryIssueKind.OUT_BEFORE_LOGIN else EntryIssueKind.DOUBLE_OUT, p.time)
                    continue
                }
                inside += Duration.between(inSince, p.time)
                lastOut = p.time
                inSince = null
                outSince = p.time
                loggedOut = p.isLogout
            }
        }

        // On a past day a final break start that never ended is most likely a logout entered as a break.
        if (now == null && !type.isDayOff && type != DayType.WFH && outSince != null && !loggedOut && lastOut != null) {
            issues += EntryIssue(EntryIssueKind.OPEN_BREAK, lastOut)
        }

        var returnBy: LocalTime? = null
        val allowance = rules.breakAllowance(firstIn)
        val status = when {
            type.isDayOff -> DayStatus.DAY_OFF
            firstIn == null -> DayStatus.NOT_STARTED
            inSince != null && now == null -> DayStatus.MISSING_CHECKOUT
            inSince != null -> {
                if (now!! > inSince) inside += Duration.between(inSince, now)
                DayStatus.INSIDE
            }
            now != null && outSince != null && !loggedOut -> {
                if (now > outSince) breaks += Duration.between(outSince, now)
                breakCount++
                val left = allowance - breaks
                if (!left.isNegative) returnBy = now.plus(left)
                DayStatus.ON_BREAK
            }
            else -> DayStatus.DONE
        }

        val live = status == DayStatus.INSIDE || status == DayStatus.ON_BREAK
        val end = if (live) now else lastOut
        val present = if (firstIn != null && end != null && end > firstIn) Duration.between(firstIn, end) else Duration.ZERO
        val remaining = if (type.isDayOff) Duration.ZERO
        else maxOf(rules.requiredInside - inside, rules.requiredPresent - present).coerceAtLeast(Duration.ZERO)
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
            breakAllowance = allowance,
            breakLeft = allowance - breaks,
            expectedLeave = expectedLeave,
            returnBy = returnBy,
            rules = rules,
            type = type,
            regularized = regularized,
            issues = issues,
            present = present,
        )
    }

    private fun wfhPunches(punches: List<Punch>): List<Punch> {
        val sorted = punches.sortedBy { it.time }
        val login = sorted.firstOrNull { it.isIn } ?: return emptyList()
        val logout = sorted.lastOrNull { !it.isIn && it.time >= login.time }
            // Logging in again after logging out means the day is still running.
            ?.takeIf { out -> sorted.none { it.isIn && it.time > out.time } }
        return listOfNotNull(login, logout?.copy(isLogout = true))
    }
}
