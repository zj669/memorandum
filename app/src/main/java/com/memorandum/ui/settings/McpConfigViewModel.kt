package com.memorandum.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorandum.data.local.room.entity.McpServerEntity
import com.memorandum.data.repository.ConfigRepository
import com.memorandum.util.CryptoHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class McpConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val configRepository: ConfigRepository,
    private val cryptoHelper: CryptoHelper,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val serverId: String? = savedStateHandle["serverId"]

    private val _uiState = MutableStateFlow(
        McpConfigUiState(
            isEditMode = serverId != null,
            serverId = serverId,
        ),
    )
    val uiState: StateFlow<McpConfigUiState> = _uiState.asStateFlow()

    init {
        if (serverId != null) {
            loadExistingConfig(serverId)
        }
    }

    private fun loadExistingConfig(id: String) {
        viewModelScope.launch {
            val server = configRepository.getMcpById(id).getOrNull() ?: return@launch
            _uiState.update {
                it.copy(
                    name = server.name,
                    baseUrl = server.baseUrl,
                    authType = try { AuthType.valueOf(server.authType) } catch (_: Exception) { AuthType.NONE },
                    authValue = server.authValueEncrypted?.let { encrypted ->
                        try { cryptoHelper.decrypt(encrypted) } catch (_: Exception) { "" }
                    }.orEmpty(),
                    toolWhitelist = server.toolWhitelistJson.joinToString(", "),
                )
            }
            validateForm()
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
        validateForm()
    }

    fun onBaseUrlChanged(url: String) {
        _uiState.update { it.copy(baseUrl = url) }
        validateForm()
    }

    fun onAuthTypeChanged(type: AuthType) {
        _uiState.update { it.copy(authType = type, authValue = "") }
        validateForm()
    }

    fun onAuthValueChanged(value: String) {
        _uiState.update { it.copy(authValue = value) }
        validateForm()
    }

    fun onToolWhitelistChanged(whitelist: String) {
        _uiState.update { it.copy(toolWhitelist = whitelist) }
    }

    fun onTestConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            try {
                val state = _uiState.value
                val result = withContext(Dispatchers.IO) {
                    testMcpConnection(state.baseUrl, state.authType, state.authValue)
                }
                _uiState.update { it.copy(isTesting = false, testResult = result) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testResult = McpTestResult.Failed(e.message ?: "连接失败"),
                    )
                }
            }
        }
    }

    private fun testMcpConnection(baseUrl: String, authType: AuthType, authValue: String): McpTestResult {
        val jsonBody = """{"jsonrpc":"2.0","method":"tools/list","id":1}"""
        val requestBuilder = Request.Builder()
            .url(baseUrl.trimEnd('/'))
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))

        when (authType) {
            AuthType.BEARER -> requestBuilder.addHeader("Authorization", "Bearer $authValue")
            AuthType.HEADER -> {
                val parts = authValue.split(":", limit = 2)
                if (parts.size == 2) {
                    requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                }
            }
            AuthType.NONE -> { }
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        return if (response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            val toolNames = try {
                // Simple extraction of tool names from JSON-RPC response
                val regex = """"name"\s*:\s*"([^"]+)"""".toRegex()
                regex.findAll(body).map { it.groupValues[1] }.toList()
            } catch (_: Exception) {
                emptyList()
            }
            McpTestResult.Success(toolNames.ifEmpty { listOf("连接成功") })
        } else {
            McpTestResult.Failed("HTTP ${response.code}: ${response.message}")
        }
    }

    fun onSave() {
        if (!_uiState.value.isValid) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val state = _uiState.value
                val encryptedAuth = if (state.authValue.isNotBlank()) {
                    cryptoHelper.encrypt(state.authValue)
                } else {
                    null
                }
                val toolList = state.toolWhitelist
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val entity = McpServerEntity(
                    id = serverId ?: UUID.randomUUID().toString(),
                    name = state.name,
                    baseUrl = state.baseUrl,
                    authType = state.authType.name,
                    authValueEncrypted = encryptedAuth,
                    enabled = true,
                    toolWhitelistJson = toolList,
                    updatedAt = System.currentTimeMillis(),
                )
                configRepository.saveMcp(entity).getOrThrow()
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onDelete() {
        val id = serverId ?: return
        viewModelScope.launch {
            try {
                val server = configRepository.getMcpById(id).getOrNull() ?: return@launch
                configRepository.deleteMcp(server).getOrThrow()
                _uiState.update { it.copy(savedSuccessfully = true) }
            } catch (_: Exception) { }
        }
    }

    private fun validateForm() {
        val state = _uiState.value
        val baseValid = state.name.isNotBlank()
            && state.baseUrl.isNotBlank()
            && state.baseUrl.startsWith("http")

        val authValid = when (state.authType) {
            AuthType.NONE -> true
            AuthType.BEARER -> state.authValue.isNotBlank()
            AuthType.HEADER -> state.authValue.contains(":")
        }

        _uiState.update { it.copy(isValid = baseValid && authValid) }
    }
}

data class McpConfigUiState(
    val isEditMode: Boolean = false,
    val serverId: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val authType: AuthType = AuthType.NONE,
    val authValue: String = "",
    val toolWhitelist: String = "",
    val isTesting: Boolean = false,
    val testResult: McpTestResult? = null,
    val isSaving: Boolean = false,
    val isValid: Boolean = false,
    val savedSuccessfully: Boolean = false,
)

enum class AuthType { NONE, BEARER, HEADER }

sealed interface McpTestResult {
    data class Success(val tools: List<String>) : McpTestResult
    data class Failed(val error: String) : McpTestResult
}
