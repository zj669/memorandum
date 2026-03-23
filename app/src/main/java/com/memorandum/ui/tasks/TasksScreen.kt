package com.memorandum.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorandum.ui.common.EmptyState
import com.memorandum.ui.common.ErrorState
import com.memorandum.ui.common.LoadingState
import com.memorandum.ui.common.MemoCard
import com.memorandum.ui.common.RiskBadge
import com.memorandum.ui.common.StatusChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        onFilterChanged = viewModel::onFilterChanged,
        onSortChanged = viewModel::onSortChanged,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onToggleSearch = viewModel::onToggleSearch,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksContent(
    uiState: TasksUiState,
    onNavigateToTask: (String) -> Unit,
    onFilterChanged: (TaskStatusFilter) -> Unit,
    onSortChanged: (TaskSortBy) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("任务") },
                actions = {
                    IconButton(onClick = onToggleSearch) {
                        Icon(
                            imageVector = if (uiState.isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (uiState.isSearchVisible) "关闭搜索" else "搜索",
                        )
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "排序")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        TaskSortBy.entries.forEach { sort ->
                            val label = when (sort) {
                                TaskSortBy.UPDATED -> "最近更新"
                                TaskSortBy.DEADLINE -> "截止时间"
                                TaskSortBy.RISK -> "风险等级"
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSortChanged(sort)
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (uiState.sortBy == sort) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Search bar
            AnimatedVisibility(visible = uiState.isSearchVisible) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索任务...") },
                    singleLine = true,
                )
            }

            // Filter chips
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskStatusFilter.entries.forEach { filter ->
                    val label = when (filter) {
                        TaskStatusFilter.ACTIVE -> "进行中"
                        TaskStatusFilter.INBOX -> "收件箱"
                        TaskStatusFilter.PLANNED -> "已规划"
                        TaskStatusFilter.DOING -> "执行中"
                        TaskStatusFilter.BLOCKED -> "已阻塞"
                        TaskStatusFilter.DONE -> "已完成"
                        TaskStatusFilter.ALL -> "全部"
                    }
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick = { onFilterChanged(filter) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("tasks_filter_${filter.name.lowercase()}"),
                    )
                }
            }

            // Content
            when {
                uiState.isLoading -> LoadingState()
                uiState.error != null -> ErrorState(
                    message = uiState.error,
                    onRetry = { onFilterChanged(uiState.selectedFilter) },
                )
                uiState.tasks.isEmpty() -> EmptyState(message = "暂无任务")
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items = uiState.tasks, key = { it.id }) { task ->
                            TaskListCard(
                                task = task,
                                onClick = { onNavigateToTask(task.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskListCard(
    task: TaskListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MemoCard(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(status = task.status)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (task.planReady) {
                Text(
                    text = "✓ 已规划",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        task.nextAction?.let { action ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "下一步: $action",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RiskBadge(riskLevel = task.riskLevel)
            task.deadlineAt?.let { deadline ->
                Text(
                    text = "截止: ${SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(deadline))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
