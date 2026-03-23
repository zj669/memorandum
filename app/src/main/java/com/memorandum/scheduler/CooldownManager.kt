package com.memorandum.scheduler

import android.util.Log
import com.memorandum.data.local.room.dao.TaskDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CooldownManager @Inject constructor(
    private val taskDao: TaskDao,
) {

    companion object {
        private const val TAG = "CooldownManager"
        private const val MIN_COOLDOWN_HOURS = 1
        private const val MAX_COOLDOWN_HOURS = 24
    }

    suspend fun isInCooldown(taskId: String): Boolean {
        val task = taskDao.getById(taskId) ?: return false
        val cooldownUntil = task.notificationCooldownUntil ?: return false
        return System.currentTimeMillis() < cooldownUntil
    }

    suspend fun setCooldown(taskId: String, hours: Int) {
        val clampedHours = hours.coerceIn(MIN_COOLDOWN_HOURS, MAX_COOLDOWN_HOURS)
        val until = System.currentTimeMillis() + clampedHours * 3600_000L
        Log.i(TAG, "Setting cooldown: taskId=$taskId, hours=$clampedHours, until=$until")
        taskDao.updateCooldown(taskId, until)
    }
}
