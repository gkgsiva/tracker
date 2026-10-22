package com.gkgsiva.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-day settings. Only days marked work-from-home have a row. */
@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val epochDay: Long,
    val isWfh: Boolean,
)
