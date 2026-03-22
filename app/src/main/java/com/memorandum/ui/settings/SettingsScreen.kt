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
fun SettingsScreen(
    onNavigateToNotifications: () -> Unit,
    onNavigateToModelConfig: () -> Unit,
    onNavigateToMcpConfig: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToModelConfig = onNavigateToModelConfig,
        onNavigateToMcpConfig = onNavigateToMcpConfig,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onNavigateToNotifications: () -> Unit,
    onNavigateToModelConfig: () -> Unit,
    onNavigateToMcpConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
