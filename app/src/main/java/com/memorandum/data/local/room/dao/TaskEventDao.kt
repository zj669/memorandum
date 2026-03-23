package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.memorandum.data.local.room.entity.TaskEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskEventDao {

    @Insert
    suspend fun insert(event: TaskEventEntity)

    @Query("SELECT * FROM task_events WHERE task_id = :taskId ORDER BY created_at DESC")
    fun observeByTask(taskId: String): Flow<List<TaskEventEntity>>

    @Query("SELECT * FROM task_events ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<TaskEventEntity>

    @Query("SELECT * FROM task_events WHERE created_at >= :timestamp ORDER BY created_at DESC")
    suspend fun getEventsSince(timestamp: Long): List<TaskEventEntity>

    @Query("DELETE FROM task_events")
    suspend fun deleteAll()
}
