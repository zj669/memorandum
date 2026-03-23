package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.memorandum.data.local.room.enums.ScheduleSource

@Entity(
    tableName = "schedule_blocks",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("task_id", "block_date"),
        Index("block_date", "start_time"),
    ],
)
data class ScheduleBlockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "block_date") val blockDate: String,
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "end_time") val endTime: String,
    val reason: String,
    val source: ScheduleSource,
    val accepted: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
