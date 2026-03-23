package com.memorandum.data.repository

import com.memorandum.data.local.room.entity.PlanStepEntity
import com.memorandum.data.local.room.entity.PrepItemEntity
import com.memorandum.data.local.room.entity.ScheduleBlockEntity
import com.memorandum.data.local.room.entity.TaskEntity
import com.memorandum.data.local.room.enums.TaskStatus
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun saveFromPlan(
        task: TaskEntity,
        steps: List<PlanStepEntity>,
        blocks: List<ScheduleBlockEntity>,
        preps: List<PrepItemEntity>,
    ): Result<Unit>

    fun observeActiveTasks(): Flow<List<TaskEntity>>
    fun observeDoneTasks(): Flow<List<TaskEntity>>
    fun observeAll(): Flow<List<TaskEntity>>
    fun observeById(id: String): Flow<TaskEntity?>
    fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>>
    fun observeTasksForDate(date: String): Flow<List<TaskEntity>>
    suspend fun updateStatus(id: String, status: TaskStatus): Result<Unit>
    suspend fun recordEvent(taskId: String, eventType: String, payload: String? = null): Result<Unit>
}
