package com.memorandum.scheduler

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.memorandum.ai.orchestrator.HeartbeatOrchestrator
import com.memorandum.ai.orchestrator.HeartbeatResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val heartbeatOrchestrator: HeartbeatOrchestrator,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HeartbeatWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Heartbeat work started, attempt=$runAttemptCount")
        return try {
            val result = heartbeatOrchestrator.executeHeartbeat()
            when (result) {
                is HeartbeatResult.Notified -> {
                    Log.i(TAG, "Heartbeat notified: id=${result.notificationId}")
                }
                is HeartbeatResult.Skipped -> {
                    Log.i(TAG, "Heartbeat skipped: ${result.reason}")
                }
                is HeartbeatResult.Failed -> {
                    Log.w(TAG, "Heartbeat failed: ${result.error}")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat work exception: ${e.message}")
            Result.success()
        }
    }
}
