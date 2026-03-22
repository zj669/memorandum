package com.memorandum.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ModelConfigViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(ModelConfigUiState())
    val uiState: StateFlow<ModelConfigUiState> = _uiState.asStateFlow()
}

data class ModelConfigUiState(
    val isLoading: Boolean = false,
)
