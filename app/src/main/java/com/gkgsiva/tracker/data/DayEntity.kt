package com.gkgsiva.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-day settings. Days without a row are normal office days. */
@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val epochDay: Long,
    /** A [com.gkgsiva.tracker.domain.DayType] name. */
    val type: String,
    /** An LOP the manager agreed to regularize; the day no longer counts as LOP. */
    @ColumnInfo(defaultValue = "0") val regularized: Boolean = false,
    /** Optional note on what was agreed. */
    val note: String? = null,
)
