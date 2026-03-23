package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_events",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("task_id", "created_at")],
)
data class TaskEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
