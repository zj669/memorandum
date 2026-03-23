package com.memorandum.scheduler

import com.memorandum.data.local.room.dao.NotificationDao
import com.memorandum.data.local.room.enums.NotificationType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationBehaviorTracker @Inject constructor(
    private val notificationDao: NotificationDao,
) {

    companion object {
        private const val CONSECUTIVE_IGNORE_THRESHOLD = 3
        private const val MS_PER_DAY = 24 * 3600_000L
    }

    suspend fun getIgnoreRate(type: NotificationType, windowDays: Int = 7): Float {
        val since = System.currentTimeMillis() - windowDays * MS_PER_DAY
        val all = notificationDao.getRecentByType(type, since)
        if (all.isEmpty()) return 0f

        val ignored = all.count { it.clickedAt == null && it.snoozedUntil == null }
        return ignored.toFloat() / all.size
    }

    suspend fun shouldRaiseThreshold(type: NotificationType): Boolean {
        val since = System.currentTimeMillis() - 7 * MS_PER_DAY
        val recent = notificationDao.getRecentByType(type, since)
        if (recent.size < CONSECUTIVE_IGNORE_THRESHOLD) return false

        val lastN = recent.take(CONSECUTIVE_IGNORE_THRESHOLD)
        return lastN.all { it.clickedAt == null && it.snoozedUntil == null }
    }
}
