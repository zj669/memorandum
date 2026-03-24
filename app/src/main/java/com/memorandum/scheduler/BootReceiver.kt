package com.memorandum.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.memorandum.data.local.room.enums.TaskStatus
import com.memorandum.di.ReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private val BLOCK_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed, restoring heartbeat schedule and alarms")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java,
        )

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val prefs = entryPoint.appPreferencesDataStore().preferences.first()
                entryPoint.heartbeatScheduleManager().scheduleHeartbeat(prefs.heartbeatFrequency)
                Log.i(TAG, "Heartbeat restored: frequency=${prefs.heartbeatFrequency}")

                val futureBlocks = entryPoint.scheduleBlockDao()
                    .getBlocksSince(0L)
                    .filter { block ->
                        parseBlockTimeToMillis(block.blockDate, block.startTime)?.let { it > now } == true
                    }

                futureBlocks.forEach { block ->
                    val task = entryPoint.taskRepository().observeById(block.taskId).first()
                    if (task == null || task.status == TaskStatus.DONE || task.status == TaskStatus.DROPPED) {
                        return@forEach
                    }
                    entryPoint.alarmScheduler().scheduleTaskAlarm(
                        alarmKey = block.id,
                        taskId = block.taskId,
                        taskTitle = task.title,
                        triggerAtMillis = parseBlockTimeToMillis(block.blockDate, block.startTime) ?: return@forEach,
                        notificationTitle = "即将开始: ${task.title}",
                        notificationBody = block.reason,
                    )
                }
                Log.i(TAG, "Restored ${futureBlocks.size} future schedule alarms")

                val snoozedNotifications = entryPoint.notificationRepository().observeAll().first()
                    .filter { notification ->
                        notification.taskRef != null &&
                            notification.snoozedUntil != null &&
                            notification.snoozedUntil > now &&
                            notification.dismissedAt == null &&
                            notification.clickedAt == null
                    }

                snoozedNotifications.forEach { notification ->
                    entryPoint.alarmScheduler().scheduleTaskAlarm(
                        alarmKey = "snooze:${notification.id}",
                        taskId = notification.taskRef ?: return@forEach,
                        taskTitle = notification.title,
                        triggerAtMillis = notification.snoozedUntil ?: return@forEach,
                        notificationTitle = notification.title,
                        notificationBody = notification.body,
                    )
                }
                Log.i(TAG, "Restored ${snoozedNotifications.size} snoozed alarms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore alarms on boot: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun parseBlockTimeToMillis(blockDate: String, startTime: String): Long? {
        return try {
            LocalDateTime.parse("${blockDate}T$startTime", BLOCK_TIME_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse block time: date=$blockDate, time=$startTime, error=${e.message}")
            null
        }
    }
}
