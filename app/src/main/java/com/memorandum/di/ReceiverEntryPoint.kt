package com.memorandum.di

import com.memorandum.data.local.datastore.AppPreferencesDataStore
import com.memorandum.data.repository.NotificationRepository
import com.memorandum.data.repository.TaskRepository
import com.memorandum.domain.usecase.memory.RecordTaskEventUseCase
import com.memorandum.scheduler.AlarmScheduler
import com.memorandum.scheduler.HeartbeatScheduleManager
import com.memorandum.scheduler.NotificationHelper
import com.memorandum.scheduler.PermissionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiverEntryPoint {
    fun notificationHelper(): NotificationHelper
    fun alarmScheduler(): AlarmScheduler
    fun permissionManager(): PermissionManager
    fun notificationRepository(): NotificationRepository
    fun taskRepository(): TaskRepository
    fun recordTaskEventUseCase(): RecordTaskEventUseCase
    fun heartbeatScheduleManager(): HeartbeatScheduleManager
    fun appPreferencesDataStore(): AppPreferencesDataStore
}
