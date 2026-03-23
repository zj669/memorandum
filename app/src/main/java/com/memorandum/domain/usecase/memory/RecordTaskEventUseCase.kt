package com.memorandum.domain.usecase.memory

import android.util.Log
import com.memorandum.data.local.room.dao.TaskEventDao
import com.memorandum.data.local.room.entity.TaskEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

class RecordTaskEventUseCase @Inject constructor(
    private val taskEventDao: TaskEventDao,
    private val triggerMemoryUpdateUseCase: TriggerMemoryUpdateUseCase,
) {

    companion object {
        private const val TAG = "RecordTaskEvent"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        // Async trigger check (don't block the caller)
        scope.launch {
            try {
                triggerMemoryUpdateUseCase.checkAndTrigger(eventType)
            } catch (e: Exception) {
                Log.w(TAG, "Memory trigger check failed: ${e.message}")
            }
        }
    }
}
