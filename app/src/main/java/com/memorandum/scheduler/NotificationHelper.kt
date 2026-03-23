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
        id: Int,
        title: String,
        body: String,
        channelId: String,
        taskRef: String?,
    ) {
        Log.i(TAG, "Sending notification: id=$id, channel=$channelId, taskRef=$taskRef")

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(taskRef))
            .addAction(buildSnoozeAction(id, taskRef))
            .addAction(buildMarkDoneAction(id, taskRef))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted: ${e.message}")
        }
    }

    fun channelForType(type: NotificationType): String = when (type) {
        NotificationType.DEADLINE_RISK -> CHANNEL_RISK
        NotificationType.HEARTBEAT_CHECK -> CHANNEL_HEARTBEAT
        else -> CHANNEL_TASK
    }

    private fun buildContentIntent(taskRef: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (taskRef != null) {
                putExtra("navigate_to", "task")
                putExtra("task_id", taskRef)
            } else {
                putExtra("navigate_to", "today")
            }
        }

        return PendingIntent.getActivity(
            context,
            taskRef?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildSnoozeAction(notificationId: Int, taskRef: String?): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra("notification_id", notificationId)
            putExtra("task_ref", taskRef)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Action.Builder(0, "Snooze", pendingIntent).build()
    }

    private fun buildMarkDoneAction(notificationId: Int, taskRef: String?): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_DONE
            putExtra("notification_id", notificationId)
            putExtra("task_ref", taskRef)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Action.Builder(0, "Done", pendingIntent).build()
    }
}
