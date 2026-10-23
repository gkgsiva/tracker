package com.gkgsiva.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-day settings. Days without a row are normal office days. */
@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val epochDay: Long,
    /** A [com.gkgsiva.tracker.domain.DayType] name. */
    val type: String,
)
