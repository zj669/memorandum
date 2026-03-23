package com.memorandum.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
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
        val notificationId = intent.getIntExtra("notification_id", -1)
        val taskRef = intent.getStringExtra("task_ref")

        Log.i(TAG, "Action received: action=${intent.action}, notificationId=$notificationId, taskRef=$taskRef")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, ReceiverEntryPoint::class.java,
        )

        when (intent.action) {
            ACTION_SNOOZE -> handleSnooze(context, notificationId, taskRef, entryPoint)
            ACTION_MARK_DONE -> handleMarkDone(context, notificationId, taskRef, entryPoint)
        }
    }

    private fun handleSnooze(
        context: Context,
        notificationId: Int,
        taskRef: String?,
        entryPoint: ReceiverEntryPoint,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)

                if (taskRef != null) {
                    val triggerAt = System.currentTimeMillis() + SNOOZE_DURATION_MS
                    entryPoint.alarmScheduler().scheduleTaskAlarm(
                        taskId = taskRef,
                        taskTitle = "",
                        triggerAtMillis = triggerAt,
                        notificationTitle = "Snoozed Reminder",
                        notificationBody = "You snoozed this task earlier",
                    )

                    entryPoint.notificationRepository().markSnoozed(
                        id = notificationId.toString(),
                        until = triggerAt,
                    )
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
        entryPoint: ReceiverEntryPoint,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)

                if (taskRef != null) {
                    entryPoint.taskRepository().updateStatus(taskRef, TaskStatus.DONE)
                    entryPoint.taskRepository().recordEvent(taskRef, "DONE", null)
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
