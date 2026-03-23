package com.memorandum.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorandum.ai.orchestrator.PlanningOrchestrator
import com.memorandum.data.local.room.entity.EntryEntity
import com.memorandum.data.local.room.enums.EntryType
import com.memorandum.data.local.room.enums.PlanningState
import com.memorandum.data.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EntryViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val planningOrchestrator: PlanningOrchestrator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EntryUiState())
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    fun onTypeSelected(type: EntryType) {
        _uiState.update { it.copy(selectedType = type) }
    }

    fun onTextChanged(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    fun onImagesAdded(uris: List<String>) {
        _uiState.update { state ->
            val combined = (state.imageUris + uris).take(5)
            state.copy(imageUris = combined)
        }
    }

    fun onImageRemoved(uri: String) {
        _uiState.update { state ->
            state.copy(imageUris = state.imageUris - uri)
        }
    }

    fun onDeadlineSet(timestamp: Long?) {
        _uiState.update { it.copy(deadline = timestamp) }
    }

    fun onPrioritySet(priority: Int?) {
        _uiState.update { it.copy(priority = priority) }
    }

    fun onEstimatedMinutesSet(minutes: Int?) {
        _uiState.update { it.copy(estimatedMinutes = minutes) }
    }

    fun onToggleOptionalFields() {
        _uiState.update { it.copy(showOptionalFields = !it.showOptionalFields) }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onSubmit() {
        val state = _uiState.value
        if (state.text.isBlank()) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val entryId = UUID.randomUUID().toString()
                val entry = EntryEntity(
                    id = entryId,
                    type = state.selectedType,
                    text = state.text,
                    createdAt = now,
                    updatedAt = now,
                    priority = state.priority,
                    deadlineAt = state.deadline,
                    estimatedMinutes = state.estimatedMinutes,
                    imageUrisJson = state.imageUris,
                    planningState = PlanningState.NOT_STARTED,
                    clarificationUsed = false,
                    clarificationQuestion = null,
                    clarificationAnswer = null,
                    lastPlannedAt = null,
                )
                entryRepository.create(entry).getOrThrow()

                // Trigger async planning in app scope (survives ViewModel destruction)
                appScope.launch {
                    try {
                        planningOrchestrator.startPlanning(entryId)
                    } catch (_: Exception) { }
                }

                _uiState.update {
                    it.copy(isSaving = false, saveResult = SaveResult.Success(entryId))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, saveResult = SaveResult.Error(e.message ?: "保存失败"))
                }
            }
        }
    }

    fun onSaveResultConsumed() {
        _uiState.update { it.copy(saveResult = null) }
    }
}

data class EntryUiState(
    val selectedType: EntryType = EntryType.TASK,
    val text: String = "",
    val imageUris: List<String> = emptyList(),
    val deadline: Long? = null,
    val priority: Int? = null,
    val estimatedMinutes: Int? = null,
    val showOptionalFields: Boolean = false,
    val isSaving: Boolean = false,
    val saveResult: SaveResult? = null,
)

sealed interface SaveResult {
    data class Success(val entryId: String) : SaveResult
    data class Error(val message: String) : SaveResult
}
