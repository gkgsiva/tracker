package com.gkgsiva.tracker.data

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
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    val time: LocalTime get() = LocalTime.ofSecondOfDay(secondOfDay.toLong())

    fun toPunch() = Punch(time, isIn)
}
