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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return
        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: return
        val actionType = intent.getStringExtra("action_type") ?: NotificationActionType.OPEN_TASK.name
        val alarmKey = intent.getStringExtra("alarm_key") ?: taskId

        Log.i(TAG, "Alarm received: alarmKey=$alarmKey, taskId=$taskId, actionType=$actionType")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java,
        )

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notificationRecordId = UUID.randomUUID().toString()
                val parsedActionType = try {
                    NotificationActionType.valueOf(actionType)
                } catch (_: IllegalArgumentException) {
                    NotificationActionType.OPEN_TASK
                }

                val notification = NotificationEntity(
                    id = notificationRecordId,
                    type = NotificationType.TIME_TO_START,
                    actionType = parsedActionType,
                    title = title,
                    body = body,
                    taskRef = taskId,
                    createdAt = System.currentTimeMillis(),
                    clickedAt = null,
                    dismissedAt = null,
                    snoozedUntil = null,
                    deliveryFailedAt = null,
                )

                entryPoint.notificationRepository().save(notification).getOrElse { error ->
                    Log.e(
                        TAG,
                        "Failed to save time-to-start notification: alarmKey=$alarmKey, taskId=$taskId, error=${error.message}",
                    )
                    return@launch
                }

                val delivered = entryPoint.notificationHelper().send(
                    id = notificationRecordId.hashCode(),
                    notificationRecordId = notificationRecordId,
                    title = title,
                    body = body,
                    channelId = entryPoint.notificationHelper().channelForType(NotificationType.TIME_TO_START),
                    taskRef = taskId,
                    actionType = parsedActionType,
                )
                if (!delivered) {
                    entryPoint.notificationRepository().markDeliveryFailed(notificationRecordId).getOrElse { error ->
                        Log.e(
                            TAG,
                            "Failed to persist time-to-start delivery failure: notificationId=$notificationRecordId, error=${error.message}",
                        )
                    }
                    Log.w(
                        TAG,
                        "Time-to-start notification delivery failed: alarmKey=$alarmKey, taskId=$taskId, notificationId=$notificationRecordId",
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
