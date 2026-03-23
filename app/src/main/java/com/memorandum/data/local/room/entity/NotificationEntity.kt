package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.memorandum.data.local.room.enums.NotificationActionType
import com.memorandum.data.local.room.enums.NotificationType

@Entity(
    tableName = "notifications",
    indices = [
        Index("task_ref", "created_at"),
        Index("type", "created_at"),
    ],
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val type: NotificationType,
    @ColumnInfo(name = "action_type") val actionType: NotificationActionType,
    val title: String,
    val body: String,
    @ColumnInfo(name = "task_ref") val taskRef: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "clicked_at") val clickedAt: Long?,
    @ColumnInfo(name = "dismissed_at") val dismissedAt: Long?,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long?,
)
