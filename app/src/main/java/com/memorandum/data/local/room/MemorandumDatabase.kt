package com.memorandum.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.memorandum.data.local.room.dao.EntryDao
import com.memorandum.data.local.room.dao.HeartbeatLogDao
import com.memorandum.data.local.room.dao.LlmConfigDao
import com.memorandum.data.local.room.dao.McpServerDao
import com.memorandum.data.local.room.dao.MemoryDao
import com.memorandum.data.local.room.dao.NotificationDao
import com.memorandum.data.local.room.dao.PlanStepDao
import com.memorandum.data.local.room.dao.PrepItemDao
import com.memorandum.data.local.room.dao.ScheduleBlockDao
import com.memorandum.data.local.room.dao.TaskDao
import com.memorandum.data.local.room.dao.TaskEventDao
import com.memorandum.data.local.room.dao.UserProfileDao
import com.memorandum.data.local.room.entity.EntryEntity
import com.memorandum.data.local.room.entity.HeartbeatLogEntity
import com.memorandum.data.local.room.entity.LlmConfigEntity
import com.memorandum.data.local.room.entity.McpServerEntity
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.entity.NotificationEntity
import com.memorandum.data.local.room.entity.PlanStepEntity
import com.memorandum.data.local.room.entity.PrepItemEntity
import com.memorandum.data.local.room.entity.ScheduleBlockEntity
import com.memorandum.data.local.room.entity.TaskEntity
import com.memorandum.data.local.room.entity.TaskEventEntity
import com.memorandum.data.local.room.entity.UserProfileEntity

@Database(
    entities = [
        EntryEntity::class,
        TaskEntity::class,
        ScheduleBlockEntity::class,
        PlanStepEntity::class,
        PrepItemEntity::class,
        MemoryEntity::class,
        UserProfileEntity::class,
        TaskEventEntity::class,
        HeartbeatLogEntity::class,
        NotificationEntity::class,
        LlmConfigEntity::class,
        McpServerEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MemorandumDatabase : RoomDatabase() {

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE entries SET planning_state = 'PLANNING' WHERE planning_state = 'GENERATING'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN delivery_failed_at INTEGER",
                )
            }
        }
    }

    abstract fun entryDao(): EntryDao
    abstract fun taskDao(): TaskDao
    abstract fun scheduleBlockDao(): ScheduleBlockDao
    abstract fun planStepDao(): PlanStepDao
    abstract fun prepItemDao(): PrepItemDao
    abstract fun memoryDao(): MemoryDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun taskEventDao(): TaskEventDao
    abstract fun heartbeatLogDao(): HeartbeatLogDao
    abstract fun notificationDao(): NotificationDao
    abstract fun llmConfigDao(): LlmConfigDao
    abstract fun mcpServerDao(): McpServerDao
}
