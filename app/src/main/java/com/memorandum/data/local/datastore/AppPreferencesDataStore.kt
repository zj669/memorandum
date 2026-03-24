package com.memorandum.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.memorandum.data.local.room.enums.HeartbeatFrequency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppPreferences(
    val heartbeatFrequency: HeartbeatFrequency = HeartbeatFrequency.MEDIUM,
    val quietHoursStart: String = "23:00",
    val quietHoursEnd: String = "07:00",
    val allowNetworkAccess: Boolean = false,
    val lastHeartbeatAt: Long = 0L,
    val onboardingCompleted: Boolean = false,
)

@Singleton
class AppPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val preferences: Flow<AppPreferences> = dataStore.data.map { prefs ->
        AppPreferences(
            heartbeatFrequency = prefs[KEY_HEARTBEAT_FREQUENCY]?.let { name ->
                try {
                    HeartbeatFrequency.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    HeartbeatFrequency.MEDIUM
                }
            } ?: HeartbeatFrequency.MEDIUM,
            quietHoursStart = prefs[KEY_QUIET_HOURS_START] ?: "23:00",
            quietHoursEnd = prefs[KEY_QUIET_HOURS_END] ?: "07:00",
            allowNetworkAccess = prefs[KEY_ALLOW_NETWORK] ?: false,
            lastHeartbeatAt = prefs[KEY_LAST_HEARTBEAT] ?: 0L,
            onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
        )
    }

    suspend fun updateHeartbeatFrequency(freq: HeartbeatFrequency) {
        dataStore.edit { it[KEY_HEARTBEAT_FREQUENCY] = freq.name }
    }

    suspend fun updateQuietHours(start: String, end: String) {
        dataStore.edit {
            it[KEY_QUIET_HOURS_START] = start
            it[KEY_QUIET_HOURS_END] = end
        }
    }

    suspend fun updateAllowNetwork(allow: Boolean) {
        dataStore.edit { it[KEY_ALLOW_NETWORK] = allow }
    }

    suspend fun updateLastHeartbeat(timestamp: Long) {
        dataStore.edit { it[KEY_LAST_HEARTBEAT] = timestamp }
    }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = true }
    }

    suspend fun getLastMemoryUpdateAt(): Long {
        return dataStore.data.map { it[KEY_LAST_MEMORY_UPDATE] ?: 0L }
            .first()
    }

    suspend fun updateLastMemoryUpdateAt(timestamp: Long) {
        dataStore.edit { it[KEY_LAST_MEMORY_UPDATE] = timestamp }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_HEARTBEAT_FREQUENCY = stringPreferencesKey("heartbeat_frequency")
        val KEY_QUIET_HOURS_START = stringPreferencesKey("quiet_hours_start")
        val KEY_QUIET_HOURS_END = stringPreferencesKey("quiet_hours_end")
        val KEY_ALLOW_NETWORK = booleanPreferencesKey("allow_network_access")
        val KEY_LAST_HEARTBEAT = longPreferencesKey("last_heartbeat_at")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_LAST_MEMORY_UPDATE = longPreferencesKey("last_memory_update_at")
    }
}
