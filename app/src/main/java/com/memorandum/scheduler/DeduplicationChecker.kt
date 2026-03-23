package com.memorandum.scheduler

import com.memorandum.data.local.room.dao.NotificationDao
import com.memorandum.data.local.room.enums.NotificationType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeduplicationChecker @Inject constructor(
    private val notificationDao: NotificationDao,
) {

    companion object {
        private const val DEFAULT_WINDOW_MS = 4 * 3600_000L // 4 hours
    }

    suspend fun isDuplicate(
        taskRef: String,
        type: NotificationType,
        windowMs: Long = DEFAULT_WINDOW_MS,
    ): Boolean {
        val since = System.currentTimeMillis() - windowMs
        val recent = notificationDao.getRecentForTaskAndType(taskRef, type, since)
        return recent.isNotEmpty()
    }
}
