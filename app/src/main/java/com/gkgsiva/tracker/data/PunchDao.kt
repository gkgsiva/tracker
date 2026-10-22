package com.gkgsiva.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PunchDao {
    @Query("SELECT * FROM punches ORDER BY epochDay, secondOfDay")
    fun observeAll(): Flow<List<PunchEntity>>

    @Insert
    suspend fun insert(punch: PunchEntity): Long

    @Update
    suspend fun update(punch: PunchEntity)

    @Delete
    suspend fun delete(punch: PunchEntity)

    @Query("SELECT epochDay FROM days WHERE isWfh = 1")
    fun observeWfhDays(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: DayEntity)

    @Query("DELETE FROM punches")
    suspend fun deleteAllPunches()

    @Query("DELETE FROM days")
    suspend fun deleteAllDays()
}
