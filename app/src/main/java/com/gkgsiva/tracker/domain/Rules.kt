package com.gkgsiva.tracker.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** Office timing policy. */
data class Rules(
    val arriveBy: LocalTime = LocalTime.of(9, 0),
    val leaveAfter: LocalTime = LocalTime.of(17, 0),
    val requiredInside: Duration = Duration.ofHours(8),
    val breakAllowance: Duration = Duration.ofHours(1),
)

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
)

/** A circle around the office gate. */
data class Gate(val latitude: Double, val longitude: Double, val radiusMeters: Int = 100)
