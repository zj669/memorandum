package com.memorandum.ui.today

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
fun TodayScreen(
    onNavigateToEntry: () -> Unit,
    onNavigateToTask: (String) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TodayContent(
        uiState = uiState,
        onNavigateToEntry = onNavigateToEntry,
        onNavigateToTask = onNavigateToTask,
        modifier = modifier,
    )
}

@Composable
private fun TodayContent(
    uiState: TodayUiState,
    onNavigateToEntry: () -> Unit,
    onNavigateToTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Today",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
