package com.memorandum.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class McpConfigViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(McpConfigUiState())
    val uiState: StateFlow<McpConfigUiState> = _uiState.asStateFlow()
}

data class McpConfigUiState(
    val isLoading: Boolean = false,
)
