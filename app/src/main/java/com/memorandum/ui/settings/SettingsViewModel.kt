package com.memorandum.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorandum.data.local.datastore.AppPreferencesDataStore
import com.memorandum.data.local.room.enums.HeartbeatFrequency
import com.memorandum.data.repository.ConfigRepository
import com.memorandum.domain.usecase.config.ClearDataUseCase
import com.memorandum.scheduler.HeartbeatScheduleManager
import com.memorandum.scheduler.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val appPreferencesDataStore: AppPreferencesDataStore,
    private val heartbeatScheduleManager: HeartbeatScheduleManager,
    private val clearDataUseCase: ClearDataUseCase,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        refreshPermissionStatus()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                configRepository.observeLlmConfigs(),
                configRepository.observeMcpServers(),
                appPreferencesDataStore.preferences,
            ) { llmConfigs, mcpServers, prefs ->
                SettingsUiState(
                    llmConfigs = llmConfigs.map { config ->
                        LlmConfigDisplay(
                            id = config.id,
                            providerName = config.providerName,
                            modelName = config.modelName,
                            supportsImage = config.supportsImage,
                        )
                    },
                    mcpServers = mcpServers.map { server ->
                        McpServerDisplay(
                            id = server.id,
                            name = server.name,
                            baseUrl = server.baseUrl,
                            enabled = server.enabled,
                        )
                    },
                    heartbeatFrequency = prefs.heartbeatFrequency.name,
                    quietHoursStart = prefs.quietHoursStart,
                    quietHoursEnd = prefs.quietHoursEnd,
                    allowNetworkAccess = prefs.allowNetworkAccess,
                )
            }.catch { e ->
                _uiState.update { it.copy(error = e.message) }
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        notificationPermissionGranted = current.notificationPermissionGranted,
                        exactAlarmPermissionGranted = current.exactAlarmPermissionGranted,
                        showClearMemoryDialog = current.showClearMemoryDialog,
                        showClearNotificationsDialog = current.showClearNotificationsDialog,
                        showClearAllDialog = current.showClearAllDialog,
                    )
                }
            }
        }
    }

    fun refreshPermissionStatus() {
        _uiState.update {
            it.copy(
                notificationPermissionGranted = permissionManager.hasNotificationPermission(),
                exactAlarmPermissionGranted = permissionManager.hasExactAlarmPermission(),
            )
        }
    }

    fun openNotificationSettings(context: Context) {
        permissionManager.openNotificationSettings(context)
    }

    fun openExactAlarmSettings(context: Context) {
        permissionManager.openExactAlarmSettings(context)
    }

    fun onToggleMcpServer(serverId: String, enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                mcpServers = state.mcpServers.map {
                    if (it.id == serverId) it.copy(enabled = enabled) else it
                },
            )
        }
        viewModelScope.launch {
            configRepository.updateMcpEnabled(serverId, enabled)
        }
    }

    fun onHeartbeatFrequencyChanged(frequency: String) {
        _uiState.update { it.copy(heartbeatFrequency = frequency) }
        viewModelScope.launch {
            val freq = try {
                HeartbeatFrequency.valueOf(frequency)
            } catch (_: IllegalArgumentException) {
                HeartbeatFrequency.MEDIUM
            }
            appPreferencesDataStore.updateHeartbeatFrequency(freq)
            heartbeatScheduleManager.scheduleHeartbeat(freq)
        }
    }

    fun onQuietHoursStartChanged(start: String) {
        _uiState.update { it.copy(quietHoursStart = start) }
        viewModelScope.launch {
            appPreferencesDataStore.updateQuietHours(start, _uiState.value.quietHoursEnd)
        }
    }

    fun onQuietHoursEndChanged(end: String) {
        _uiState.update { it.copy(quietHoursEnd = end) }
        viewModelScope.launch {
            appPreferencesDataStore.updateQuietHours(_uiState.value.quietHoursStart, end)
        }
    }

    fun onAllowNetworkChanged(allow: Boolean) {
        _uiState.update { it.copy(allowNetworkAccess = allow) }
        viewModelScope.launch {
            appPreferencesDataStore.updateAllowNetwork(allow)
        }
    }

    fun onShowClearMemoryDialog() {
        _uiState.update { it.copy(showClearMemoryDialog = true) }
    }

    fun onDismissClearMemoryDialog() {
        _uiState.update { it.copy(showClearMemoryDialog = false) }
    }

    fun onConfirmClearMemory() {
        _uiState.update { it.copy(showClearMemoryDialog = false) }
        viewModelScope.launch {
            try {
                clearDataUseCase.clearMemories()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear memory data: ${e.message}")
                _uiState.update { it.copy(error = "清除记忆数据失败") }
            }
        }
    }

    fun onShowClearNotificationsDialog() {
        _uiState.update { it.copy(showClearNotificationsDialog = true) }
    }

    fun onDismissClearNotificationsDialog() {
        _uiState.update { it.copy(showClearNotificationsDialog = false) }
    }

    fun onConfirmClearNotifications() {
        _uiState.update { it.copy(showClearNotificationsDialog = false) }
        viewModelScope.launch {
            try {
                clearDataUseCase.clearNotifications()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear notification data: ${e.message}")
                _uiState.update { it.copy(error = "清除通知历史失败") }
            }
        }
    }

    fun onShowClearAllDialog() {
        _uiState.update { it.copy(showClearAllDialog = true) }
    }

    fun onDismissClearAllDialog() {
        _uiState.update { it.copy(showClearAllDialog = false) }
    }

    fun onConfirmClearAll() {
        _uiState.update { it.copy(showClearAllDialog = false) }
        viewModelScope.launch {
            try {
                clearDataUseCase.clearAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear all app data: ${e.message}")
                _uiState.update { it.copy(error = "清除全部数据失败") }
            }
        }
    }
}

data class SettingsUiState(
    val llmConfigs: List<LlmConfigDisplay> = emptyList(),
    val mcpServers: List<McpServerDisplay> = emptyList(),
    val heartbeatFrequency: String = "MEDIUM",
    val quietHoursStart: String = "23:00",
    val quietHoursEnd: String = "07:00",
    val allowNetworkAccess: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val exactAlarmPermissionGranted: Boolean = false,
    val showClearMemoryDialog: Boolean = false,
    val showClearNotificationsDialog: Boolean = false,
    val showClearAllDialog: Boolean = false,
    val error: String? = null,
)

data class LlmConfigDisplay(
    val id: String,
    val providerName: String,
    val modelName: String,
    val supportsImage: Boolean,
)

data class McpServerDisplay(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean,
)
