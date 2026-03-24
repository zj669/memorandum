package com.memorandum.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.memorandum.data.local.room.enums.NotificationActionType
import com.memorandum.data.local.room.enums.NotificationType
import com.memorandum.data.local.room.enums.TaskStatus
import com.memorandum.di.ReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotifActionReceiver"
        const val ACTION_SNOOZE = "com.memorandum.ACTION_SNOOZE"
        const val ACTION_MARK_DONE = "com.memorandum.ACTION_MARK_DONE"
        private const val SNOOZE_DURATION_MS = 60 * 60 * 1000L // 1 hour
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        val taskRef = intent.getStringExtra(NotificationHelper.EXTRA_TASK_REF)
        val notificationDbId = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_DB_ID)
        val reminderId = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID)
        val actionTypeName = intent.getStringExtra(NotificationHelper.EXTRA_ACTION_TYPE)
        val notificationTypeName = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_TYPE)

        Log.i(
            TAG,
            "Action received: action=${intent.action}, notificationId=$notificationId, taskRef=$taskRef, dbId=$notificationDbId, reminderId=$reminderId",
        )

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, ReceiverEntryPoint::class.java,
        )

        when (intent.action) {
            ACTION_SNOOZE -> handleSnooze(
                context = context,
                notificationId = notificationId,
                taskRef = taskRef,
                notificationDbId = notificationDbId,
                reminderId = reminderId,
                actionTypeName = actionTypeName,
                notificationTypeName = notificationTypeName,
                entryPoint = entryPoint,
            )
            ACTION_MARK_DONE -> handleMarkDone(
                context = context,
                notificationId = notificationId,
                taskRef = taskRef,
                notificationDbId = notificationDbId,
                reminderId = reminderId,
                entryPoint = entryPoint,
            )
        }
    }

    private fun handleSnooze(
        context: Context,
        notificationId: Int,
        taskRef: String?,
        notificationDbId: String?,
        reminderId: String?,
        actionTypeName: String?,
        notificationTypeName: String?,
        entryPoint: ReceiverEntryPoint,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)

                if (taskRef != null) {
                    val triggerAt = System.currentTimeMillis() + SNOOZE_DURATION_MS
                    val requestKey = notificationDbId ?: reminderId ?: taskRef
                    val actionType = actionTypeName
                        ?.let { runCatching { NotificationActionType.valueOf(it) }.getOrNull() }
                        ?: NotificationActionType.OPEN_TASK
                    val notificationType = notificationTypeName
                        ?.let { runCatching { NotificationType.valueOf(it) }.getOrNull() }
                        ?: NotificationType.TIME_TO_START
                    entryPoint.alarmScheduler().scheduleTaskAlarm(
                        taskId = taskRef,
                        taskTitle = "",
                        triggerAtMillis = triggerAt,
                        notificationTitle = "稍后提醒: 继续处理任务",
                        notificationBody = "你之前选择了稍后提醒，回来继续推进吧。",
                        requestCodeKey = requestKey,
                        actionType = actionType,
                        notificationType = notificationType,
                        notificationDbId = notificationDbId,
                    )

                    notificationDbId?.let {
                        entryPoint.notificationRepository().markSnoozed(
                            id = it,
                            until = triggerAt,
                        )
                    }
                    entryPoint.recordTaskEventUseCase().record(taskRef, "SNOOZE", triggerAt.toString())
                }

                Log.i(TAG, "Snooze handled: taskRef=$taskRef, snoozed for 1 hour")
            } catch (e: Exception) {
                Log.e(TAG, "Snooze failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkDone(
        context: Context,
        notificationId: Int,
        taskRef: String?,
        notificationDbId: String?,
        reminderId: String?,
        entryPoint: ReceiverEntryPoint,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)
                notificationDbId?.let { entryPoint.notificationRepository().markClicked(it) }

                if (taskRef != null) {
                    entryPoint.taskRepository().updateStatus(taskRef, TaskStatus.DONE)
                    entryPoint.recordTaskEventUseCase().record(taskRef, "DONE")
                    reminderId?.let { entryPoint.alarmScheduler().cancelTaskAlarm(it) }
                    entryPoint.alarmScheduler().cancelAllAlarmsForTask(taskRef)
                }

                Log.i(TAG, "Mark done handled: taskRef=$taskRef")
            } catch (e: Exception) {
                Log.e(TAG, "Mark done failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
