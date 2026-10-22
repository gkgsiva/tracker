package com.gkgsiva.tracker.domain

import java.time.Duration
import java.time.LocalTime

/** Office timing policy. */
data class Rules(
    val arriveBy: LocalTime = LocalTime.of(9, 0),
    val leaveAfter: LocalTime = LocalTime.of(17, 0),
    val requiredInside: Duration = Duration.ofHours(8),
    val breakAllowance: Duration = Duration.ofHours(1),
)
