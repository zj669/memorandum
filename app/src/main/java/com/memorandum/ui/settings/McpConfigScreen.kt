package com.memorandum.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun McpConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: McpConfigViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    McpConfigContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
private fun McpConfigContent(
    uiState: McpConfigUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "MCP Config",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
