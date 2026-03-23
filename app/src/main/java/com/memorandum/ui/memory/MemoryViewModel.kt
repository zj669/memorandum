package com.memorandum.ui.memory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorandum.data.local.room.entity.MemoryEntity
import com.memorandum.data.local.room.enums.MemoryType
import com.memorandum.domain.usecase.memory.MemoryDisplayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryDisplayUseCase: MemoryDisplayUseCase,
) : ViewModel() {

    companion object {
        private const val TAG = "MemoryViewModel"
    }

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    private val typeFilter = MutableStateFlow<MemoryType?>(null)

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                typeFilter.flatMapLatest { filter ->
                    memoryDisplayUseCase.observeMemories(filter)
                },
                memoryDisplayUseCase.observeProfile(),
            ) { memories, profile ->
                MemoryUiState(
                    selectedType = typeFilter.value,
                    memories = memories.map { it.toDisplayItem() },
                    userProfileSummary = profile?.profileJson,
                    isProfileExpanded = _uiState.value.isProfileExpanded,
                    isLoading = false,
                    error = null,
                )
            }
                .catch { e ->
                    Log.e(TAG, "Error observing memory data: ${e.message}")
                    emit(
                        _uiState.value.copy(
                            isLoading = false,
                            error = "Failed to load memories: ${e.message}",
                        )
                    )
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun onTypeFilterChanged(type: MemoryType?) {
        typeFilter.value = type
    }

    fun onDeleteMemory(memoryId: String) {
        viewModelScope.launch {
            try {
                memoryDisplayUseCase.deleteMemory(memoryId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete memory: memoryId=$memoryId, error=${e.message}")
                _uiState.update { it.copy(error = "Failed to delete memory") }
            }
        }
    }

    fun onToggleProfileExpanded() {
        _uiState.update { it.copy(isProfileExpanded = !it.isProfileExpanded) }
    }

    private fun MemoryEntity.toDisplayItem() = MemoryDisplayItem(
        id = id,
        type = type,
        subject = subject,
        content = content,
        confidence = confidence,
        evidenceCount = evidenceCount,
        lastUsedAt = lastUsedAt,
    )
}

data class MemoryUiState(
    val selectedType: MemoryType? = null,
    val memories: List<MemoryDisplayItem> = emptyList(),
    val userProfileSummary: String? = null,
    val isProfileExpanded: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class MemoryDisplayItem(
    val id: String,
    val type: MemoryType,
    val subject: String,
    val content: String,
    val confidence: Float,
    val evidenceCount: Int,
    val lastUsedAt: Long?,
)
