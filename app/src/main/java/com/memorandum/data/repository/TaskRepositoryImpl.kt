package com.memorandum.data.repository

import androidx.room.withTransaction
import com.memorandum.data.local.room.MemorandumDatabase
import com.memorandum.data.local.room.dao.PlanStepDao
import com.memorandum.data.local.room.dao.PrepItemDao
import com.memorandum.data.local.room.dao.ScheduleBlockDao
import com.memorandum.data.local.room.dao.TaskDao
import com.memorandum.data.local.room.dao.TaskEventDao
import com.memorandum.data.local.room.entity.PlanStepEntity
import com.memorandum.data.local.room.entity.PrepItemEntity
import com.memorandum.data.local.room.entity.ScheduleBlockEntity
import com.memorandum.data.local.room.entity.TaskEntity
import com.memorandum.data.local.room.entity.TaskEventEntity
import com.memorandum.data.local.room.enums.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val db: MemorandumDatabase,
    private val taskDao: TaskDao,
    private val planStepDao: PlanStepDao,
    private val scheduleBlockDao: ScheduleBlockDao,
    private val prepItemDao: PrepItemDao,
    private val taskEventDao: TaskEventDao,
) : TaskRepository {

    override suspend fun saveFromPlan(
        task: TaskEntity,
        steps: List<PlanStepEntity>,
        blocks: List<ScheduleBlockEntity>,
        preps: List<PrepItemEntity>,
    ): Result<Unit> = runCatching {
        db.withTransaction {
            taskDao.upsert(task)
            planStepDao.deleteByTask(task.id)
            planStepDao.insertAll(steps)
            scheduleBlockDao.deletePlannerBlocksForTask(task.id)
            scheduleBlockDao.insertAll(blocks)
            prepItemDao.deleteByTask(task.id)
            prepItemDao.insertAll(preps)
        }
    }

    override fun observeActiveTasks(): Flow<List<TaskEntity>> =
        taskDao.observeActiveTasks()

    override fun observeDoneTasks(): Flow<List<TaskEntity>> =
        taskDao.observeDoneTasks()

    override fun observeAll(): Flow<List<TaskEntity>> =
        taskDao.observeAll()

    override fun observeById(id: String): Flow<TaskEntity?> =
        taskDao.observeById(id)

    override fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>> =
        taskDao.observeByStatus(status)

    override fun observeTasksForDate(date: String): Flow<List<TaskEntity>> =
        taskDao.observeTasksForDate(date)

    override suspend fun updateStatus(id: String, status: TaskStatus): Result<Unit> =
        runCatching {
            taskDao.updateStatus(id, status, System.currentTimeMillis())
        }

    override suspend fun recordEvent(
        taskId: String,
        eventType: String,
        payload: String?,
    ): Result<Unit> = runCatching {
        taskEventDao.insert(
            TaskEventEntity(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                eventType = eventType,
                payloadJson = payload,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
