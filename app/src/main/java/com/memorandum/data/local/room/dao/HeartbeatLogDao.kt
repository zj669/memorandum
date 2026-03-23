package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.memorandum.data.local.room.entity.HeartbeatLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartbeatLogDao {

    @Insert
    suspend fun insert(log: HeartbeatLogEntity)

    @Query("SELECT * FROM heartbeat_logs ORDER BY checked_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<HeartbeatLogEntity>

    @Query("SELECT * FROM heartbeat_logs ORDER BY checked_at DESC LIMIT 1")
    fun observeLatest(): Flow<HeartbeatLogEntity?>
}
