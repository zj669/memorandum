package com.memorandum.domain.usecase.memory

import android.util.Log
import com.memorandum.ai.orchestrator.MemoryOrchestrator
import com.memorandum.ai.orchestrator.MemoryUpdateResult
import com.memorandum.data.local.datastore.AppPreferencesDataStore
import com.memorandum.data.local.room.dao.TaskEventDao
import javax.inject.Inject

class TriggerMemoryUpdateUseCase @Inject constructor(
    private val taskEventDao: TaskEventDao,
    private val memoryOrchestrator: MemoryOrchestrator,
    private val appPreferencesDataStore: AppPreferencesDataStore,
) {

    companion object {
        private const val TAG = "TriggerMemoryUpdate"
        const val MIN_EVENTS_THRESHOLD = 5
        const val MIN_INTERVAL_HOURS = 6
    }

    suspend fun checkAndTrigger(triggerEvent: String): MemoryUpdateResult {
        val immediateEvents = setOf("DONE", "ACCEPTED_PLAN")

        if (triggerEvent in immediateEvents) {
            Log.i(TAG, "Immediate trigger for event: $triggerEvent")
            val result = memoryOrchestrator.updateMemories(force = true)
            if (result is MemoryUpdateResult.Updated) {
                appPreferencesDataStore.updateLastMemoryUpdateAt(System.currentTimeMillis())
            }
            return result
        }

        // Accumulated trigger
        val lastUpdateAt = appPreferencesDataStore.getLastMemoryUpdateAt()
        val hoursSinceUpdate = (System.currentTimeMillis() - lastUpdateAt) / 3600_000L
        if (hoursSinceUpdate < MIN_INTERVAL_HOURS) {
            Log.d(TAG, "Skipped: only ${hoursSinceUpdate}h since last update (min=$MIN_INTERVAL_HOURS)")
            return MemoryUpdateResult.Skipped
        }

        val recentEvents = taskEventDao.getEventsSince(lastUpdateAt)
        if (recentEvents.size < MIN_EVENTS_THRESHOLD) {
            Log.d(TAG, "Skipped: only ${recentEvents.size} events (min=$MIN_EVENTS_THRESHOLD)")
            return MemoryUpdateResult.Skipped
        }

        Log.i(TAG, "Accumulated trigger: ${recentEvents.size} events, ${hoursSinceUpdate}h since last update")
        val result = memoryOrchestrator.updateMemories()
        if (result is MemoryUpdateResult.Updated) {
            appPreferencesDataStore.updateLastMemoryUpdateAt(System.currentTimeMillis())
        }
        return result
    }
}
