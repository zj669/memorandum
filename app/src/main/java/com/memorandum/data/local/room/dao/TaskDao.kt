package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.memorandum.data.local.room.entity.TaskEntity
import com.memorandum.data.local.room.enums.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY last_progress_at DESC")
    fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status NOT IN ('DONE', 'DROPPED') ORDER BY risk_level DESC, last_progress_at ASC")
    fun observeActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'DONE' ORDER BY last_progress_at DESC")
    fun observeDoneTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY last_progress_at DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status NOT IN ('DONE', 'DROPPED')")
    suspend fun getActiveTasks(): List<TaskEntity>

    @Query(
        """SELECT t.* FROM tasks t
        INNER JOIN schedule_blocks sb ON t.id = sb.task_id
        WHERE sb.block_date = :date
        ORDER BY sb.start_time ASC""",
    )
    fun observeTasksForDate(date: String): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET status = :status, last_progress_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: TaskStatus, now: Long)

    @Query("UPDATE tasks SET last_heartbeat_at = :now WHERE id = :id")
    suspend fun updateHeartbeatTime(id: String, now: Long)

    @Query("UPDATE tasks SET notification_cooldown_until = :until WHERE id = :id")
    suspend fun updateCooldown(id: String, until: Long)
}
