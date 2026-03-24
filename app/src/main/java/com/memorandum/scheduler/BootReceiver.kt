package com.memorandum.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.memorandum.di.ReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed, restoring heartbeat schedule")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, ReceiverEntryPoint::class.java,
        )

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = entryPoint.appPreferencesDataStore().preferences.first()
                entryPoint.heartbeatScheduleManager().scheduleHeartbeat(prefs.heartbeatFrequency)
                entryPoint.alarmScheduler().restoreFutureAlarms()
                Log.i(TAG, "Heartbeat and alarms restored: frequency=${prefs.heartbeatFrequency}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore heartbeat on boot: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
