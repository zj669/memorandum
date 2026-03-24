package com.memorandum.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.memorandum.data.local.room.dao.NotificationDao
import com.memorandum.data.local.room.dao.ScheduleBlockDao
import com.memorandum.data.local.room.dao.TaskDao
import com.memorandum.data.local.room.enums.NotificationActionType
import com.memorandum.data.local.room.enums.NotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleBlockDao: ScheduleBlockDao,
    private val taskDao: TaskDao,
    private val notificationDao: NotificationDao,
) {

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_ACTION_TYPE = "action_type"
        private const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        private const val EXTRA_NOTIFICATION_DB_ID = "notification_db_id"
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTaskAlarm(
        taskId: String,
        taskTitle: String,
        triggerAtMillis: Long,
        notificationTitle: String,
        notificationBody: String,
        requestCodeKey: String = taskId,
        actionType: NotificationActionType = NotificationActionType.OPEN_TASK,
        notificationType: NotificationType = NotificationType.TIME_TO_START,
        notificationDbId: String? = null,
    ) {
        Log.i(
            TAG,
            "Scheduling alarm: taskId=$taskId, requestKey=$requestCodeKey, triggerAt=$triggerAtMillis",
        )

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_REMINDER_ID, requestCodeKey)
            putExtra(EXTRA_TITLE, notificationTitle)
            putExtra(EXTRA_BODY, notificationBody)
            putExtra(EXTRA_ACTION_TYPE, actionType.name)
            putExtra(EXTRA_NOTIFICATION_TYPE, notificationType.name)
            notificationDbId?.let { putExtra(EXTRA_NOTIFICATION_DB_ID, it) }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
                )
            } else {
                Log.w(TAG, "Cannot schedule exact alarms, permission not granted")
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
            )
        }
    }

    fun cancelTaskAlarm(requestCodeKey: String) {
        Log.i(TAG, "Cancelling alarm: requestKey=$requestCodeKey")

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeKey.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    suspend fun cancelAllAlarmsForTask(taskId: String) {
        cancelTaskAlarm(taskId)
        scheduleBlockDao.getByTask(taskId).forEach { block ->
            cancelTaskAlarm(block.id)
        }
    }

    suspend fun restoreFutureAlarms() {
        restoreFutureScheduleBlockAlarms()
        restorePendingSnoozedAlarms()
    }

    private suspend fun restoreFutureScheduleBlockAlarms() {
        val today = LocalDate.now().format(DATE_FMT)
        val nowTime = LocalTime.now().format(TIME_FMT)
        val futureBlocks = scheduleBlockDao.getFutureBlocks(today = today, nowTime = nowTime)

        futureBlocks.forEach { block ->
            val triggerAtMillis = parseBlockTimeToMillis(block.blockDate, block.startTime) ?: return@forEach
            val task = taskDao.getById(block.taskId) ?: return@forEach
            scheduleTaskAlarm(
                taskId = block.taskId,
                taskTitle = task.title,
                triggerAtMillis = triggerAtMillis,
                notificationTitle = "即将开始: ${task.title}",
                notificationBody = block.reason,
                requestCodeKey = block.id,
            )
        }

        Log.i(TAG, "Restored ${futureBlocks.size} future schedule alarms")
    }

    private suspend fun restorePendingSnoozedAlarms() {
        val now = System.currentTimeMillis()
        val snoozedNotifications = notificationDao.getPendingSnoozed(now)

        snoozedNotifications.forEach { notification ->
            val taskRef = notification.taskRef ?: return@forEach
            val triggerAt = notification.snoozedUntil ?: return@forEach
            scheduleTaskAlarm(
                taskId = taskRef,
                taskTitle = notification.title,
                triggerAtMillis = triggerAt,
                notificationTitle = notification.title,
                notificationBody = notification.body,
                requestCodeKey = notification.id,
                actionType = notification.actionType,
                notificationType = notification.type,
                notificationDbId = notification.id,
            )
        }

        Log.i(TAG, "Restored ${snoozedNotifications.size} snoozed alarms")
    }

    private fun parseBlockTimeToMillis(blockDate: String, startTime: String): Long? {
        return try {
            LocalDateTime.parse(
                "${blockDate}T${startTime}",
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse block time: date=$blockDate, time=$startTime, error=${e.message}")
            null
        }
    }
}
