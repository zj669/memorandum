package com.memorandum.data.repository

import com.memorandum.data.local.room.dao.NotificationDao
import com.memorandum.data.local.room.entity.NotificationEntity
import com.memorandum.data.local.room.enums.NotificationType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao,
) : NotificationRepository {

    override suspend fun save(notification: NotificationEntity): Result<Unit> = runCatching {
        notificationDao.insert(notification)
    }

    override fun observeAll(): Flow<List<NotificationEntity>> =
        notificationDao.observeAll()

    override suspend fun isDuplicate(
        taskRef: String,
        type: NotificationType,
        windowMs: Long,
    ): Result<Boolean> = runCatching {
        val since = System.currentTimeMillis() - windowMs
        notificationDao.getRecentForTaskAndType(taskRef, type, since).isNotEmpty()
    }

    override suspend fun markClicked(id: String): Result<Unit> = runCatching {
        notificationDao.markClicked(id, System.currentTimeMillis())
    }

    override suspend fun markDismissed(id: String): Result<Unit> = runCatching {
        notificationDao.markDismissed(id, System.currentTimeMillis())
    }

    override suspend fun markSnoozed(id: String, until: Long): Result<Unit> = runCatching {
        notificationDao.markSnoozed(id, until)
    }

    override suspend fun markDeliveryFailed(id: String): Result<Unit> = runCatching {
        notificationDao.markDeliveryFailed(id, System.currentTimeMillis())
    }
}
