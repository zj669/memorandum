package com.memorandum.domain.usecase.config

import com.memorandum.data.local.room.MemorandumDatabase
import com.memorandum.data.local.datastore.AppPreferencesDataStore
import com.memorandum.scheduler.HeartbeatScheduleManager
import javax.inject.Inject

class ClearDataUseCase @Inject constructor(
    private val database: MemorandumDatabase,
    private val appPreferencesDataStore: AppPreferencesDataStore,
    private val heartbeatScheduleManager: HeartbeatScheduleManager,
) {

    suspend fun clearMemories() {
        database.memoryDao().deleteAll()
        database.taskEventDao().deleteAll()
    }

    suspend fun clearNotifications() {
        database.notificationDao().deleteAll()
    }

    suspend fun clearAll() {
        database.clearAllTables()
        heartbeatScheduleManager.pauseHeartbeat()
    }
}
