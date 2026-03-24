package com.memorandum.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.memorandum.data.local.room.entity.NotificationEntity
import com.memorandum.data.local.room.enums.NotificationActionType
import com.memorandum.data.local.room.enums.NotificationType
import com.memorandum.di.ReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_ACTION_TYPE = "action_type"
        private const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        private const val EXTRA_NOTIFICATION_DB_ID = "notification_db_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val actionTypeName = intent.getStringExtra(EXTRA_ACTION_TYPE) ?: NotificationActionType.OPEN_TASK.name
        val notificationTypeName = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE) ?: NotificationType.TIME_TO_START.name
        val notificationDbId = intent.getStringExtra(EXTRA_NOTIFICATION_DB_ID)

        Log.i(TAG, "Alarm received: taskId=$taskId, actionType=$actionTypeName, reminderId=$reminderId")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, ReceiverEntryPoint::class.java,
        )
        val actionType = runCatching { NotificationActionType.valueOf(actionTypeName) }
            .getOrDefault(NotificationActionType.OPEN_TASK)
        val notificationType = runCatching { NotificationType.valueOf(notificationTypeName) }
            .getOrDefault(NotificationType.TIME_TO_START)
        val persistentNotificationId = notificationDbId ?: UUID.randomUUID().toString()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (notificationDbId == null) {
                    entryPoint.notificationRepository().save(
                        NotificationEntity(
                            id = persistentNotificationId,
                            type = notificationType,
                            actionType = actionType,
                            title = title,
                            body = body,
                            taskRef = taskId,
                            createdAt = System.currentTimeMillis(),
                            clickedAt = null,
                            dismissedAt = null,
                            snoozedUntil = null,
                        ),
                    ).onFailure { error ->
                        Log.w(TAG, "Failed to persist alarm notification: ${error.message}")
                    }
                }

                val delivered = entryPoint.notificationHelper().send(
                    systemNotificationKey = reminderId ?: taskId,
                    title = title,
                    body = body,
                    channelId = entryPoint.notificationHelper().channelForType(notificationType),
                    taskRef = taskId,
                    actionType = actionType,
                    notificationType = notificationType,
                    notificationDbId = persistentNotificationId,
                    reminderId = reminderId,
                )
                if (!delivered) {
                    Log.w(TAG, "Alarm notification not delivered: taskId=$taskId")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
