package com.gkgsiva.tracker.ui

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
