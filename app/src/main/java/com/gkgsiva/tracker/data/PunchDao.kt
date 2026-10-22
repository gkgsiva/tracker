package com.gkgsiva.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
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
}
