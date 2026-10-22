package com.gkgsiva.tracker.ui

import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.LopReason
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

fun LocalTime.formatClock(): String = format(timeFormat)

/** "7h 05m"; negative durations get a leading minus. */
fun Duration.formatHm(): String {
    val totalMin = abs(toMinutes())
    val sign = if (isNegative) "-" else ""
    return "%s%dh %02dm".format(sign, totalMin / 60, totalMin % 60)
}

/** "7:05:42", used for the live counters. */
fun Duration.formatHms(): String {
    val s = abs(seconds)
    val sign = if (isNegative) "-" else ""
    return "%s%d:%02d:%02d".format(sign, s / 3600, (s % 3600) / 60, s % 60)
}

fun Duration.hours(): Float = seconds / 3600f

fun LopReason.shortLabel(): String = when (this) {
    LopReason.LATE_LOGIN -> "late login"
    LopReason.EARLY_EXIT -> "left early"
    LopReason.UNDER_8H -> "under 8h"
    LopReason.MISSING_CHECKOUT -> "no check-out"
}

fun LopReason.describe(s: DaySummary): String = when (this) {
    LopReason.LATE_LOGIN -> "Logged in at ${s.firstIn?.formatClock()}, after ${s.rules.arriveBy.formatClock()}"
    LopReason.EARLY_EXIT -> "Left at ${s.lastOut?.formatClock()}, before ${s.rules.leaveAfter.formatClock()}"
    LopReason.UNDER_8H -> "Only ${s.inside.formatHm()} inside, 8h required"
    LopReason.MISSING_CHECKOUT -> "No check-out recorded"
}
