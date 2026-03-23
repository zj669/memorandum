package com.memorandum.ui.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorandum.data.local.room.enums.MemoryType
import com.memorandum.ui.common.ConfirmDialog
import com.memorandum.ui.common.EmptyState
import com.memorandum.ui.common.ErrorState
import com.memorandum.ui.common.LoadingState
import com.memorandum.ui.common.MemoCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MemoryContent(
        uiState = uiState,
        onTypeFilterChanged = viewModel::onTypeFilterChanged,
        onDeleteMemory = viewModel::onDeleteMemory,
        onToggleProfileExpanded = viewModel::onToggleProfileExpanded,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryContent(
    uiState: MemoryUiState,
    onTypeFilterChanged: (MemoryType?) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onToggleProfileExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    if (deleteTarget != null) {
        ConfirmDialog(
            title = "删除记忆",
            message = "确定要删除这条记忆吗？此操作不可撤销。",
            isDestructive = true,
            onConfirm = {
                deleteTarget?.let { onDeleteMemory(it) }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("AI 记忆") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Type filter chips
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.selectedType == null,
                    onClick = { onTypeFilterChanged(null) },
                    label = { Text("全部") },
                )
                MemoryType.entries.forEach { type ->
                    val label = when (type) {
                        MemoryType.PREFERENCE -> "偏好"
                        MemoryType.PATTERN -> "习惯"
                        MemoryType.LONG_TERM_GOAL -> "长期目标"
                        MemoryType.TASK_CONTEXT -> "任务上下文"
                        MemoryType.PREP_TEMPLATE -> "准备模板"
                    }
                    FilterChip(
                        selected = uiState.selectedType == type,
                        onClick = { onTypeFilterChanged(type) },
                        label = { Text(label) },
                    )
                }
            }

            when {
                uiState.isLoading -> LoadingState()
                uiState.error != null -> ErrorState(
                    message = uiState.error,
                    onRetry = { onTypeFilterChanged(uiState.selectedType) },
                )
                uiState.memories.isEmpty() && uiState.userProfileSummary == null -> {
                    EmptyState(message = "暂无 AI 记忆")
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // User profile summary card
                        uiState.userProfileSummary?.let { summary ->
                            item(key = "user_profile") {
                                UserProfileCard(
                                    summary = summary,
                                    isExpanded = uiState.isProfileExpanded,
                                    onToggle = onToggleProfileExpanded,
                                )
                            }
                        }

                        if (uiState.memories.isEmpty()) {
                            item(key = "empty_memories") {
                                EmptyState(message = "暂无此类记忆")
                            }
                        } else {
                            items(
                                items = uiState.memories,
                                key = { it.id },
                            ) { memory ->
                                MemoryCard(
                                    memory = memory,
                                    onLongPress = { deleteTarget = memory.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserProfileCard(
    summary: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MemoCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "用户画像",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggle) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                )
            }
        }
        AnimatedVisibility(visible = isExpanded) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryCard(
    memory: MemoryDisplayItem,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeLabel = when (memory.type) {
        MemoryType.PREFERENCE -> "偏好"
        MemoryType.PATTERN -> "习惯"
        MemoryType.LONG_TERM_GOAL -> "长期目标"
        MemoryType.TASK_CONTEXT -> "任务上下文"
        MemoryType.PREP_TEMPLATE -> "准备模板"
    }

    MemoCard(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = onLongPress,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = memory.subject,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = memory.content,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "置信度",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { memory.confidence },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(memory.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "证据: ${memory.evidenceCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            memory.lastUsedAt?.let { ts ->
                Text(
                    text = "最近使用: ${SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(ts))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
