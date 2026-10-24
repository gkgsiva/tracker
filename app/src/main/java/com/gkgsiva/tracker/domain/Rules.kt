package com.gkgsiva.tracker.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/**
 * Office timing policy. [arriveBy] and [leaveAfter] are the core hours (breaking either is LOP);
 * [officeStart] to [officeEnd] are the official hours, which set how much break you get.
 */
data class Rules(
    val arriveBy: LocalTime = LocalTime.of(9, 0),
    val leaveAfter: LocalTime = LocalTime.of(17, 0),
    val requiredInside: Duration = Duration.ofHours(8),
    val officeStart: LocalTime = LocalTime.of(8, 30),
    val officeEnd: LocalTime = LocalTime.of(17, 30),
    /** Hours needed on a half day, at any time of day. */
    val halfDayHours: Duration = Duration.ofHours(4),
    /** Time off on a short-leave day: that much less inside, and core hours eased by it at both ends. */
    val shortLeave: Duration = Duration.ofHours(2),
    /** When set, the break allowance regardless of login time (half days and short leave). */
    val fixedBreak: Duration? = null,
) {
    /**
     * Break you can take and still leave by [officeEnd]: the time from login (or [officeStart]
     * before logging in) to [officeEnd], minus the required hours. Logging in at 8:30 gives 1h,
     * earlier gives more, later gives less. Going over isn't LOP; you just stay later.
     */
    fun breakAllowance(firstIn: LocalTime?): Duration = fixedBreak
        ?: (Duration.between(firstIn ?: officeStart, officeEnd) - requiredInside).coerceAtLeast(Duration.ZERO)

    /**
     * The rules a day of [type] is held to. A half day needs [halfDayHours] with no core hours; a
     * short-leave day needs [shortLeave] less, may start that much after [arriveBy] and end that
     * much before [leaveAfter]. Both keep the usual break allowance from an on-time login.
     */
    fun forType(type: DayType): Rules = when (type) {
        DayType.HALF_DAY -> copy(
            requiredInside = halfDayHours,
            arriveBy = LocalTime.MAX,
            leaveAfter = LocalTime.MIN,
            fixedBreak = breakAllowance(null),
        )
        DayType.SHORT_LEAVE -> copy(
            requiredInside = (requiredInside - shortLeave).coerceAtLeast(Duration.ZERO),
            arriveBy = arriveBy.plus(shortLeave),
            leaveAfter = leaveAfter.minus(shortLeave),
            fixedBreak = breakAllowance(null),
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
    /** Break length offered first when starting a break; null means no planned length. */
    val breakPlanMinutes: Int? = 15,
    /** How long before a planned break ends to send the heads-up. */
    val breakPlanLeadMinutes: Int = 2,
)

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
