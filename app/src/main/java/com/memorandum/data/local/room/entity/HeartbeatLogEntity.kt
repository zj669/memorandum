package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.memorandum.data.local.room.enums.NotificationType

@Entity(tableName = "heartbeat_logs")
data class HeartbeatLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "checked_at") val checkedAt: Long,
    @ColumnInfo(name = "should_notify") val shouldNotify: Boolean,
    @ColumnInfo(name = "notification_type") val notificationType: NotificationType?,
    val reason: String,
    @ColumnInfo(name = "task_ref") val taskRef: String?,
    @ColumnInfo(name = "used_mcp") val usedMcp: Boolean,
    @ColumnInfo(name = "mcp_summary") val mcpSummary: String?,
)
