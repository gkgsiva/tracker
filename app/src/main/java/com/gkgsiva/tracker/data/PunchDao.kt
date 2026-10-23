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

    @Query("SELECT * FROM days")
    fun observeDays(): Flow<List<DayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: DayEntity)

    @Query("DELETE FROM punches")
    suspend fun deleteAllPunches()

    @Query("DELETE FROM days")
    suspend fun deleteAllDays()

    @Query("SELECT * FROM punches WHERE epochDay = :epochDay ORDER BY secondOfDay")
    suspend fun punchesFor(epochDay: Long): List<PunchEntity>

    @Query("SELECT * FROM days WHERE epochDay = :epochDay")
    suspend fun day(epochDay: Long): DayEntity?

    @Query("SELECT * FROM punches ORDER BY epochDay, secondOfDay")
    suspend fun allPunches(): List<PunchEntity>

    @Query("SELECT * FROM days ORDER BY epochDay")
    suspend fun allDays(): List<DayEntity>

    @Insert
    suspend fun insertAll(punches: List<PunchEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDays(days: List<DayEntity>)
}
