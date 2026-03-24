package com.memorandum.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val EXTRA_ALARM_KEY = "alarm_key"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_ACTION_TYPE = "action_type"
    }

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTaskAlarm(
        alarmKey: String,
        taskId: String,
        taskTitle: String,
        triggerAtMillis: Long,
        notificationTitle: String,
        notificationBody: String,
    ) {
        Log.i(TAG, "Scheduling alarm: alarmKey=$alarmKey, taskId=$taskId, triggerAt=$triggerAtMillis")

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_KEY, alarmKey)
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, notificationTitle)
            putExtra(EXTRA_BODY, notificationBody)
            putExtra(EXTRA_ACTION_TYPE, "OPEN_TASK")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                Log.w(TAG, "Cannot schedule exact alarms for $alarmKey, permission not granted")
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    fun cancelTaskAlarm(alarmKey: String) {
        Log.i(TAG, "Cancelling alarm: alarmKey=$alarmKey")

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmKey.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun cancelAllAlarmsForTask(taskId: String) {
        cancelTaskAlarm(taskId)
    }
}
