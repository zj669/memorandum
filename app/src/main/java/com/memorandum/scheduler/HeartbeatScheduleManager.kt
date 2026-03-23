package com.memorandum.scheduler

import android.util.Log
import androidx.lifecycle.asFlow
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.memorandum.data.local.room.enums.HeartbeatFrequency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartbeatScheduleManager @Inject constructor(
    private val workManager: WorkManager,
) {

    companion object {
        private const val TAG = "HeartbeatScheduleMgr"
        const val HEARTBEAT_WORK_NAME = "memorandum_heartbeat"
    }

    fun scheduleHeartbeat(frequency: HeartbeatFrequency) {
        Log.i(TAG, "Scheduling heartbeat: frequency=$frequency, interval=${frequency.intervalMinutes}min")

        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            frequency.intervalMinutes, TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10, TimeUnit.MINUTES,
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            HEARTBEAT_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun pauseHeartbeat() {
        Log.i(TAG, "Pausing heartbeat")
        workManager.cancelUniqueWork(HEARTBEAT_WORK_NAME)
    }

    fun observeHeartbeatStatus(): Flow<WorkInfo?> {
        return workManager.getWorkInfosForUniqueWorkLiveData(HEARTBEAT_WORK_NAME)
            .asFlow()
            .map { workInfos -> workInfos.firstOrNull() }
    }
}
