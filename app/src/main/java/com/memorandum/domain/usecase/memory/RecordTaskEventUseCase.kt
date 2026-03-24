package com.memorandum.domain.usecase.memory

import android.util.Log
import com.memorandum.data.local.room.dao.TaskEventDao
import com.memorandum.data.local.room.entity.TaskEventEntity
import java.util.UUID
import javax.inject.Inject

class RecordTaskEventUseCase @Inject constructor(
    private val taskEventDao: TaskEventDao,
    private val triggerMemoryUpdateUseCase: TriggerMemoryUpdateUseCase,
) {

    companion object {
        private const val TAG = "RecordTaskEvent"
    }

    suspend fun record(
        taskId: String,
        eventType: String,
        payload: String? = null,
    ) {
        val event = TaskEventEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            eventType = eventType,
            payloadJson = payload,
            createdAt = System.currentTimeMillis(),
        )
        taskEventDao.insert(event)
        Log.i(TAG, "Recorded event: taskId=$taskId, type=$eventType")

        runCatching {
            triggerMemoryUpdateUseCase.checkAndTrigger(eventType)
        }.onFailure { error ->
            Log.w(TAG, "Memory trigger check failed: taskId=$taskId, type=$eventType, error=${error.message}")
        }
    }
}
