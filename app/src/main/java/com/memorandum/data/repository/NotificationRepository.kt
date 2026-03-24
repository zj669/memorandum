package com.memorandum.data.repository

import com.memorandum.data.local.room.entity.NotificationEntity
import com.memorandum.data.local.room.enums.NotificationType
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    suspend fun save(notification: NotificationEntity): Result<Unit>
    fun observeAll(): Flow<List<NotificationEntity>>
    suspend fun isDuplicate(taskRef: String, type: NotificationType, windowMs: Long): Result<Boolean>
    suspend fun markClicked(id: String): Result<Unit>
    suspend fun markDismissed(id: String): Result<Unit>
    suspend fun markSnoozed(id: String, until: Long): Result<Unit>
    suspend fun markDeliveryFailed(id: String): Result<Unit>
}
