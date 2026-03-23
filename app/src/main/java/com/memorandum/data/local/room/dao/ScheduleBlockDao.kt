package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.memorandum.data.local.room.entity.ScheduleBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleBlockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<ScheduleBlockEntity>)

    @Query("SELECT * FROM schedule_blocks WHERE task_id = :taskId ORDER BY block_date, start_time")
    fun observeByTask(taskId: String): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE block_date = :date ORDER BY start_time")
    fun observeByDate(date: String): Flow<List<ScheduleBlockEntity>>

    @Query("DELETE FROM schedule_blocks WHERE task_id = :taskId AND source = 'PLANNER'")
    suspend fun deletePlannerBlocksForTask(taskId: String)

    @Query("UPDATE schedule_blocks SET accepted = 1 WHERE id = :id")
    suspend fun accept(id: String)

    @Query("SELECT * FROM schedule_blocks WHERE created_at >= :since ORDER BY block_date, start_time")
    suspend fun getBlocksSince(since: Long): List<ScheduleBlockEntity>
}
