package com.memorandum.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.memorandum.MainActivity
import com.memorandum.data.local.room.enums.NotificationActionType
import com.memorandum.data.local.room.enums.TaskStatus
import com.memorandum.di.ReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotifActionReceiver"
        const val ACTION_OPEN = "com.memorandum.ACTION_OPEN"
        const val ACTION_SNOOZE = "com.memorandum.ACTION_SNOOZE"
        const val ACTION_MARK_DONE = "com.memorandum.ACTION_MARK_DONE"
        private const val SNOOZE_DURATION_MS = 60 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", -1)
        val notificationRecordId = intent.getStringExtra("notification_record_id")
        val taskRef = intent.getStringExtra("task_ref")

        Log.i(
            TAG,
            "Action received: action=${intent.action}, notificationId=$notificationId, notificationRecordId=$notificationRecordId, taskRef=$taskRef",
        )

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java,
        )

        when (intent.action) {
            ACTION_OPEN -> handleOpen(context, notificationId, notificationRecordId, taskRef, intent, entryPoint)
            ACTION_SNOOZE -> handleSnooze(context, notificationId, notificationRecordId, taskRef, entryPoint)
            ACTION_MARK_DONE -> handleMarkDone(context, notificationId, notificationRecordId, taskRef, entryPoint)
        }
    }

    private fun handleOpen(
        context: Context,
        notificationId: Int,
        notificationRecordId: String?,
        taskRef: String?,
        intent: Intent,
        entryPoint: ReceiverEntryPoint,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (notificationRecordId != null) {
                    entryPoint.notificationRepository().markClicked(notificationRecordId)
                }

                val actionType = intent.getStringExtra("action_type") ?: NotificationActionType.OPEN_TASK.name
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    when (actionType) {
                        NotificationActionType.OPEN_TODAY.name -> {
                            putExtra("navigate_to", "today")
                        }
                        else -> {
                            if (taskRef != null) {
                                putExtra("navigate_to", "task")
                                putExtra("task_id", taskRef)
                            } else {
                                putExtra("navigate_to", "today")
                            }
                        }
                    }
                }
                context.startActivity(openIntent)
                NotificationManagerCompat.from(context).cancel(notificationId)
            } catch (e: Exception) {
                Log.e(TAG, "Open notification failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnooze(
        context: Context,
        notificationId: Int,
        notificationRecordId: String?,
        taskRef: String?,
        entryPoint: ReceiverEntryPoint,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)

                if (taskRef != null && notificationRecordId != null) {
                    val triggerAt = System.currentTimeMillis() + SNOOZE_DURATION_MS
                    entryPoint.alarmScheduler().scheduleTaskAlarm(
                        alarmKey = "snooze:$notificationRecordId",
                        taskId = taskRef,
                        taskTitle = "",
                        triggerAtMillis = triggerAt,
                        notificationTitle = "稍后提醒：继续处理任务",
                        notificationBody = "这是你稍后提醒的任务",
                    )

                    entryPoint.notificationRepository().markSnoozed(
                        id = notificationRecordId,
                        until = triggerAt,
                    )
                    entryPoint.recordTaskEventUseCase().record(taskRef, "SNOOZE", notificationRecordId)
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
        notificationRecordId: String?,
        taskRef: String?,
        entryPoint: ReceiverEntryPoint,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)

                if (taskRef != null) {
                    entryPoint.taskRepository().updateStatus(taskRef, TaskStatus.DONE)
                    entryPoint.recordTaskEventUseCase().record(taskRef, "DONE", notificationRecordId)

                    entryPoint.scheduleBlockDao()
                        .getBlocksSince(0L)
                        .filter { it.taskId == taskRef }
                        .forEach { block ->
                            entryPoint.alarmScheduler().cancelTaskAlarm(block.id)
                        }

                    if (notificationRecordId != null) {
                        entryPoint.alarmScheduler().cancelTaskAlarm("snooze:$notificationRecordId")
                    }
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
