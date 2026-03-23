package com.memorandum.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.memorandum.di.ReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return
        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: return
        val actionType = intent.getStringExtra("action_type") ?: "OPEN_TASK"

        Log.i(TAG, "Alarm received: taskId=$taskId, actionType=$actionType")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, ReceiverEntryPoint::class.java,
        )
        entryPoint.notificationHelper().send(
            id = taskId.hashCode(),
            title = title,
            body = body,
            channelId = NotificationHelper.CHANNEL_TASK,
            taskRef = taskId,
        )
    }
}
