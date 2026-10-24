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
) {
    /**
     * Break you can take and still leave by [officeEnd]: the time from login (or [officeStart]
     * before logging in) to [officeEnd], minus the required hours. Logging in at 8:30 gives 1h,
     * earlier gives more, later gives less. Going over isn't LOP; you just stay later.
     */
    fun breakAllowance(firstIn: LocalTime?): Duration =
        (Duration.between(firstIn ?: officeStart, officeEnd) - requiredInside).coerceAtLeast(Duration.ZERO)
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
    /** Where you swipe in; when set, passing it prompts a login/logout. */
    val gate: Gate? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** A circle around the office gate. */
data class Gate(val latitude: Double, val longitude: Double, val radiusMeters: Int = 100)
