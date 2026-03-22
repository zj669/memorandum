package com.memorandum.ui.tasks

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
fun TasksScreen(
    onNavigateToTask: (String) -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TasksContent(
        uiState = uiState,
        onNavigateToTask = onNavigateToTask,
        modifier = modifier,
    )
}

@Composable
private fun TasksContent(
    uiState: TasksUiState,
    onNavigateToTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Tasks",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
