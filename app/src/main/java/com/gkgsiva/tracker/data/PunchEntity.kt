package com.gkgsiva.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gkgsiva.tracker.domain.Punch
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "punches", indices = [Index("epochDay")])
data class PunchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val secondOfDay: Int,
    val isIn: Boolean,
    /** For check-outs: true when it's the final logout of the day rather than a break. */
    @ColumnInfo(defaultValue = "0") val isLogout: Boolean = false,
    /** For break starts: what the break was for, a [com.gkgsiva.tracker.domain.BreakLabel] name. */
    val label: String? = null,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    val time: LocalTime get() = LocalTime.ofSecondOfDay(secondOfDay.toLong())

    fun toPunch() = Punch(time, isIn, isLogout, label)
}
