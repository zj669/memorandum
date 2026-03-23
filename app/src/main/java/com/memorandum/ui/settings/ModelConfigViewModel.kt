package com.memorandum.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorandum.data.local.room.entity.LlmConfigEntity
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
class ModelConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val configRepository: ConfigRepository,
    private val cryptoHelper: CryptoHelper,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val configId: String? = savedStateHandle["configId"]

    private val _uiState = MutableStateFlow(
        ModelConfigUiState(
            isEditMode = configId != null,
            configId = configId,
        ),
    )
    val uiState: StateFlow<ModelConfigUiState> = _uiState.asStateFlow()

    init {
        if (configId != null) {
            loadExistingConfig(configId)
        }
    }

    private fun loadExistingConfig(id: String) {
        viewModelScope.launch {
            val config = configRepository.getLlmById(id).getOrNull() ?: return@launch
            _uiState.update {
                it.copy(
                    providerName = config.providerName,
                    baseUrl = config.baseUrl,
                    modelName = config.modelName,
                    apiKey = try { cryptoHelper.decrypt(config.apiKeyEncrypted) } catch (_: Exception) { "" },
                    supportsImage = config.supportsImage,
                )
            }
            validateForm()
        }
    }

    fun onProviderNameChanged(name: String) {
        _uiState.update { it.copy(providerName = name) }
        validateForm()
    }

    fun onBaseUrlChanged(url: String) {
        _uiState.update { it.copy(baseUrl = url) }
        validateForm()
    }

    fun onModelNameChanged(model: String) {
        _uiState.update { it.copy(modelName = model) }
        validateForm()
    }

    fun onApiKeyChanged(key: String) {
        _uiState.update { it.copy(apiKey = key) }
        validateForm()
    }

    fun onSupportsImageChanged(supports: Boolean) {
        _uiState.update { it.copy(supportsImage = supports) }
    }

    fun onPresetSelected(preset: LlmPreset) {
        _uiState.update {
            it.copy(
                providerName = preset.name,
                baseUrl = preset.baseUrl,
                modelName = preset.suggestedModels.firstOrNull().orEmpty(),
                supportsImage = preset.supportsImage,
            )
        }
        validateForm()
    }

    fun onTestConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            try {
                val state = _uiState.value
                val result = withContext(Dispatchers.IO) {
                    testLlmConnection(state.baseUrl, state.apiKey, state.modelName)
                }
                _uiState.update { it.copy(isTesting = false, testResult = result) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testResult = ConnectionTestResult.Failed(e.message ?: "连接失败"),
                    )
                }
            }
        }
    }

    private fun testLlmConnection(baseUrl: String, apiKey: String, modelName: String): ConnectionTestResult {
        val url = baseUrl.trimEnd('/') + "/v1/chat/completions"
        val jsonBody = """{"model":"$modelName","messages":[{"role":"user","content":"ping"}],"max_tokens":5}"""
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        return if (response.isSuccessful) {
            val body = response.body?.string()?.take(200).orEmpty()
            ConnectionTestResult.Success("连接成功: $body")
        } else {
            ConnectionTestResult.Failed("HTTP ${response.code}: ${response.message}")
        }
    }

    fun onSave() {
        if (!_uiState.value.isValid) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val state = _uiState.value
                val entity = LlmConfigEntity(
                    id = configId ?: UUID.randomUUID().toString(),
                    providerName = state.providerName,
                    baseUrl = state.baseUrl,
                    modelName = state.modelName,
                    apiKeyEncrypted = cryptoHelper.encrypt(state.apiKey),
                    supportsImage = state.supportsImage,
                    updatedAt = System.currentTimeMillis(),
                )
                configRepository.saveLlm(entity).getOrThrow()
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onDelete() {
        val id = configId ?: return
        viewModelScope.launch {
            try {
                val config = configRepository.getLlmById(id).getOrNull() ?: return@launch
                configRepository.deleteLlm(config).getOrThrow()
                _uiState.update { it.copy(savedSuccessfully = true) }
            } catch (_: Exception) { }
        }
    }

    private fun validateForm() {
        val state = _uiState.value
        val valid = state.providerName.isNotBlank()
            && state.baseUrl.isNotBlank()
            && state.baseUrl.startsWith("http")
            && state.modelName.isNotBlank()
            && state.apiKey.isNotBlank()
        _uiState.update { it.copy(isValid = valid) }
    }
}

data class ModelConfigUiState(
    val isEditMode: Boolean = false,
    val configId: String? = null,
    val providerName: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val supportsImage: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val isSaving: Boolean = false,
    val isValid: Boolean = false,
    val savedSuccessfully: Boolean = false,
)

sealed interface ConnectionTestResult {
    data class Success(val responsePreview: String) : ConnectionTestResult
    data class Failed(val error: String) : ConnectionTestResult
}
