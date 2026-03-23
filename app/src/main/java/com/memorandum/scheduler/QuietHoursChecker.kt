package com.memorandum.scheduler

import android.util.Log
import com.memorandum.data.local.datastore.AppPreferencesDataStore
import com.memorandum.data.local.room.enums.NotificationType
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuietHoursChecker @Inject constructor(
    private val appPreferencesDataStore: AppPreferencesDataStore,
) {

    companion object {
        private const val TAG = "QuietHoursChecker"
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }

    suspend fun isInQuietHours(): Boolean {
        val prefs = appPreferencesDataStore.preferences.first()
        val now = LocalTime.now()
        return try {
            val start = LocalTime.parse(prefs.quietHoursStart, TIME_FMT)
            val end = LocalTime.parse(prefs.quietHoursEnd, TIME_FMT)
            if (start.isBefore(end)) {
                now.isAfter(start) && now.isBefore(end)
            } else {
                // Wraps around midnight: e.g. 23:00 - 07:00
                now.isAfter(start) || now.isBefore(end)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse quiet hours: ${e.message}")
            false
        }
    }

    fun canOverrideQuietHours(type: NotificationType): Boolean {
        return type == NotificationType.DEADLINE_RISK
    }
}
