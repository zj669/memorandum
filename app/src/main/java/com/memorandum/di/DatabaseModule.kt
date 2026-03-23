package com.memorandum.di

import android.content.Context
import androidx.room.Room
import com.memorandum.data.local.room.MemorandumDatabase
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
import com.memorandum.data.repository.ConfigRepository
import com.memorandum.data.repository.ConfigRepositoryImpl
import com.memorandum.data.repository.EntryRepository
import com.memorandum.data.repository.EntryRepositoryImpl
import com.memorandum.data.repository.MemoryRepository
import com.memorandum.data.repository.MemoryRepositoryImpl
import com.memorandum.data.repository.NotificationRepository
import com.memorandum.data.repository.NotificationRepositoryImpl
import com.memorandum.data.repository.TaskRepository
import com.memorandum.data.repository.TaskRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MemorandumDatabase =
        Room.databaseBuilder(
            context,
            MemorandumDatabase::class.java,
            "memorandum.db",
        ).addMigrations(MemorandumDatabase.MIGRATION_1_2).build()

    @Provides fun provideEntryDao(db: MemorandumDatabase): EntryDao = db.entryDao()
    @Provides fun provideTaskDao(db: MemorandumDatabase): TaskDao = db.taskDao()
    @Provides fun provideScheduleBlockDao(db: MemorandumDatabase): ScheduleBlockDao = db.scheduleBlockDao()
    @Provides fun providePlanStepDao(db: MemorandumDatabase): PlanStepDao = db.planStepDao()
    @Provides fun providePrepItemDao(db: MemorandumDatabase): PrepItemDao = db.prepItemDao()
    @Provides fun provideMemoryDao(db: MemorandumDatabase): MemoryDao = db.memoryDao()
    @Provides fun provideUserProfileDao(db: MemorandumDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun provideTaskEventDao(db: MemorandumDatabase): TaskEventDao = db.taskEventDao()
    @Provides fun provideHeartbeatLogDao(db: MemorandumDatabase): HeartbeatLogDao = db.heartbeatLogDao()
    @Provides fun provideNotificationDao(db: MemorandumDatabase): NotificationDao = db.notificationDao()
    @Provides fun provideLlmConfigDao(db: MemorandumDatabase): LlmConfigDao = db.llmConfigDao()
    @Provides fun provideMcpServerDao(db: MemorandumDatabase): McpServerDao = db.mcpServerDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindEntryRepository(impl: EntryRepositoryImpl): EntryRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: ConfigRepositoryImpl): ConfigRepository
}
