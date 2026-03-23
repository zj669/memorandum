package com.memorandum.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.memorandum.data.local.room.entity.PlanStepEntity
import com.memorandum.data.local.room.enums.StepStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanStepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(steps: List<PlanStepEntity>)

    @Query("SELECT * FROM plan_steps WHERE task_id = :taskId ORDER BY step_index")
    fun observeByTask(taskId: String): Flow<List<PlanStepEntity>>

    @Query("UPDATE plan_steps SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: StepStatus, now: Long)

    @Query("DELETE FROM plan_steps WHERE task_id = :taskId")
    suspend fun deleteByTask(taskId: String)
}
