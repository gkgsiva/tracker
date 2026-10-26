package com.gkgsiva.tracker.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/**
 * Office timing policy. Breaking any one of these makes the day LOP:
 * - log in by [arriveBy] and log out after [leaveAfter] (the core hours),
 * - [requiredInside] of work, breaks not counted,
 * - [requiredPresent] in the office from login to logout, breaks counted.
 */
data class Rules(
    val arriveBy: LocalTime = LocalTime.of(9, 0),
    val leaveAfter: LocalTime = LocalTime.of(17, 0),
    /** Work time needed, breaks excluded. */
    val requiredInside: Duration = Duration.ofHours(8),
    /** Time needed from login to logout, breaks included. */
    val requiredPresent: Duration = Duration.ofHours(9),
    /** Work time needed on a half day, at any time of day. */
    val halfDayHours: Duration = Duration.ofHours(4),
    /** Time off on a short-leave day: that much less of both, and core hours eased by it at both ends. */
    val shortLeave: Duration = Duration.ofHours(2),
) {
    /**
     * Break you can take and still leave at the earliest moment the rules allow: the later of
     * login + [requiredPresent] and [leaveAfter], minus [requiredInside]. With the defaults that's 1h;
     * logging in before 8:00 gives more. Going over isn't LOP by itself; you just stay later.
     */
    fun breakAllowance(firstIn: LocalTime?): Duration {
        val span = if (firstIn == null) requiredPresent else maxOf(requiredPresent, Duration.between(firstIn, leaveAfter))
        return (span - requiredInside).coerceAtLeast(Duration.ZERO)
    }

    /**
     * The rules a day of [type] is held to. A half day needs [halfDayHours] of work with no core
     * hours; a short-leave day needs [shortLeave] less of both, may start that much after
     * [arriveBy] and end that much before [leaveAfter]. Working from home is held to the same 9h
     * from login to logout as an office day.
     */
    fun forType(type: DayType): Rules = when (type) {
        DayType.HALF_DAY -> copy(
            requiredInside = halfDayHours,
            requiredPresent = halfDayHours,
            arriveBy = LocalTime.MAX,
            leaveAfter = LocalTime.MIN,
        )
        DayType.SHORT_LEAVE -> copy(
            requiredInside = (requiredInside - shortLeave).coerceAtLeast(Duration.ZERO),
            requiredPresent = (requiredPresent - shortLeave).coerceAtLeast(Duration.ZERO),
            arriveBy = arriveBy.plus(shortLeave),
            leaveAfter = leaveAfter.minus(shortLeave),
        )
        else -> this
    }
}

/** Everything the user can change in Settings. */
data class Settings(
    val rules: Rules = Rules(),
    val weeklyOffs: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    /** Remind 15 minutes before the login deadline on working days. */
    val remindLogin: Boolean = true,
    /** Notify when you've done the required hours and it's past the leave-after time. */
    val remindLeave: Boolean = true,
    /** Notify when the break allowance runs out during a break. */
    val remindBreak: Boolean = true,
    /** Ongoing notification with a countdown while logged in. */
    val statusNotification: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Folder (a document tree URI) for the weekly automatic backup; off when null. */
    val backupFolder: String? = null,
    /** Notify on the 1st with a summary of the month that just ended. */
    val monthSummary: Boolean = true,
    /** The limit "Set limit" puts on a break in progress. */
    val breakPlanMinutes: Int = 15,
    /** How much "Extend" adds to a break's limit. */
    val breakExtendMinutes: Int = 5,
    /** How long before a planned break ends to send the heads-up. */
    val breakPlanLeadMinutes: Int = 2,
)

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
