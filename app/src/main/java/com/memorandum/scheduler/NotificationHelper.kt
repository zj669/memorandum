package com.memorandum.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memorandum.MainActivity
import com.memorandum.R
import com.memorandum.data.local.room.enums.NotificationActionType
import com.memorandum.data.local.room.enums.NotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "NotificationHelper"
        const val CHANNEL_TASK = "memorandum_task"
        const val CHANNEL_HEARTBEAT = "memorandum_heartbeat"
        const val CHANNEL_RISK = "memorandum_risk"

        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_REF = "task_ref"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_NOTIFICATION_DB_ID = "notification_db_id"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_ACTION_TYPE = "action_type"
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
    }

    fun createChannels() {
        Log.i(TAG, "Creating notification channels")
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val taskChannel = NotificationChannel(
            CHANNEL_TASK,
            "Task Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Reminders for scheduled tasks"
        }

        val heartbeatChannel = NotificationChannel(
            CHANNEL_HEARTBEAT,
            "Heartbeat Checks",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Periodic heartbeat check notifications"
        }

        val riskChannel = NotificationChannel(
            CHANNEL_RISK,
            "Deadline Risks",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "High-priority deadline risk alerts"
        }

        manager.createNotificationChannels(listOf(taskChannel, heartbeatChannel, riskChannel))
    }

    fun send(
        systemNotificationKey: String,
        title: String,
        body: String,
        channelId: String,
        taskRef: String?,
        actionType: NotificationActionType = NotificationActionType.OPEN_TASK,
        notificationType: NotificationType = NotificationType.TIME_TO_START,
        notificationDbId: String? = null,
        reminderId: String? = null,
    ): Boolean {
        val systemNotificationId = systemNotificationKey.hashCode()
        Log.i(
            TAG,
            "Sending notification: systemId=$systemNotificationId, channel=$channelId, taskRef=$taskRef, dbId=$notificationDbId",
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(
                buildContentIntent(
                    systemNotificationId = systemNotificationId,
                    actionType = actionType,
                    taskRef = taskRef,
                    notificationDbId = notificationDbId,
                ),
            )

        if (taskRef != null) {
            builder
                .addAction(
                    buildSnoozeAction(
                        systemNotificationId = systemNotificationId,
                        taskRef = taskRef,
                        notificationDbId = notificationDbId,
                        reminderId = reminderId,
                        actionType = actionType,
                        notificationType = notificationType,
                    ),
                )
                .addAction(
                    buildMarkDoneAction(
                        systemNotificationId = systemNotificationId,
                        taskRef = taskRef,
                        notificationDbId = notificationDbId,
                        reminderId = reminderId,
                        actionType = actionType,
                        notificationType = notificationType,
                    ),
                )
        }

        return try {
            NotificationManagerCompat.from(context).notify(systemNotificationId, builder.build())
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted: ${e.message}")
            false
        }
    }

    fun channelForType(type: NotificationType): String = when (type) {
        NotificationType.DEADLINE_RISK -> CHANNEL_RISK
        NotificationType.HEARTBEAT_CHECK -> CHANNEL_HEARTBEAT
        else -> CHANNEL_TASK
    }

    private fun buildContentIntent(
        systemNotificationId: Int,
        actionType: NotificationActionType,
        taskRef: String?,
        notificationDbId: String?,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            when {
                actionType == NotificationActionType.OPEN_TODAY || taskRef == null -> {
                    putExtra(EXTRA_NAVIGATE_TO, "today")
                }
                else -> {
                    putExtra(EXTRA_NAVIGATE_TO, "task")
                    putExtra(EXTRA_TASK_ID, taskRef)
                }
            }
            notificationDbId?.let { putExtra(EXTRA_NOTIFICATION_DB_ID, it) }
        }

        return PendingIntent.getActivity(
            context,
            systemNotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildSnoozeAction(
        systemNotificationId: Int,
        taskRef: String,
        notificationDbId: String?,
        reminderId: String?,
        actionType: NotificationActionType,
        notificationType: NotificationType,
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(EXTRA_NOTIFICATION_ID, systemNotificationId)
            putExtra(EXTRA_TASK_REF, taskRef)
            putExtra(EXTRA_ACTION_TYPE, actionType.name)
            putExtra(EXTRA_NOTIFICATION_TYPE, notificationType.name)
            notificationDbId?.let { putExtra(EXTRA_NOTIFICATION_DB_ID, it) }
            reminderId?.let { putExtra(EXTRA_REMINDER_ID, it) }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            systemNotificationId * 10 + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Action.Builder(0, "稍后提醒", pendingIntent).build()
    }

    private fun buildMarkDoneAction(
        systemNotificationId: Int,
        taskRef: String,
        notificationDbId: String?,
        reminderId: String?,
        actionType: NotificationActionType,
        notificationType: NotificationType,
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_DONE
            putExtra(EXTRA_NOTIFICATION_ID, systemNotificationId)
            putExtra(EXTRA_TASK_REF, taskRef)
            putExtra(EXTRA_ACTION_TYPE, actionType.name)
            putExtra(EXTRA_NOTIFICATION_TYPE, notificationType.name)
            notificationDbId?.let { putExtra(EXTRA_NOTIFICATION_DB_ID, it) }
            reminderId?.let { putExtra(EXTRA_REMINDER_ID, it) }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            systemNotificationId * 10 + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Action.Builder(0, "标记完成", pendingIntent).build()
    }
}
