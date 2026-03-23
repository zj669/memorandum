package com.memorandum.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.memorandum.data.local.room.enums.TaskStatus

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["entry_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("status", "last_progress_at"),
        Index("risk_level", "status"),
        Index("goal_id"),
        Index("entry_id"),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    val title: String,
    val status: TaskStatus,
    val summary: String,
    @ColumnInfo(name = "goal_id") val goalId: String?,
    @ColumnInfo(name = "next_action") val nextAction: String?,
    @ColumnInfo(name = "risk_level") val riskLevel: Int,
    @ColumnInfo(name = "plan_version") val planVersion: Int,
    @ColumnInfo(name = "plan_ready") val planReady: Boolean,
    @ColumnInfo(name = "last_heartbeat_at") val lastHeartbeatAt: Long?,
    @ColumnInfo(name = "last_progress_at") val lastProgressAt: Long?,
    @ColumnInfo(name = "notification_cooldown_until") val notificationCooldownUntil: Long?,
)
