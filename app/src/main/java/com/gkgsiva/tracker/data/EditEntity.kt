package com.gkgsiva.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A note of a change made to a day's entries by hand, kept so the day's history can be explained. */
@Entity(tableName = "edits", indices = [Index("epochDay")])
data class EditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** When the change was made, in epoch millis. */
    val atMillis: Long,
    /** What changed, e.g. "login 9:05 AM → 8:55 AM". */
    val change: String,
    /** Why, if given. */
    val reason: String? = null,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}
