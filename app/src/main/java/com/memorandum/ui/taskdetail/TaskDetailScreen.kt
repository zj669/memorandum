package com.memorandum.ui.taskdetail

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorandum.data.local.room.enums.TaskStatus
import com.memorandum.ui.common.EmptyState
import com.memorandum.ui.common.ErrorState
import com.memorandum.ui.common.ImageAttachmentRow
import com.memorandum.ui.common.LoadingState
import com.memorandum.ui.common.MemoCard
import com.memorandum.ui.common.PrepItem
import com.memorandum.ui.common.PriorityIndicator
import com.memorandum.ui.common.RiskBadge
import com.memorandum.ui.common.StatusChip
import com.memorandum.ui.common.StepItem
import com.memorandum.ui.common.TimeBlockCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskDetailScreen(
    taskId: String,
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TaskDetailContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onStatusChange = viewModel::onStatusChange,
        onToggleStatusMenu = viewModel::onToggleStatusMenu,
        onStepStatusChange = viewModel::onStepStatusChange,
        onPrepStatusChange = viewModel::onPrepStatusChange,
        onScheduleAccepted = viewModel::onScheduleAccepted,
        onReplan = viewModel::onReplan,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailContent(
    uiState: TaskDetailUiState,
    onNavigateBack: () -> Unit,
    onStatusChange: (TaskStatus) -> Unit,
    onToggleStatusMenu: () -> Unit,
    onStepStatusChange: (String, com.memorandum.data.local.room.enums.StepStatus) -> Unit,
    onPrepStatusChange: (String, com.memorandum.data.local.room.enums.PrepStatus) -> Unit,
    onScheduleAccepted: (String) -> Unit,
    onReplan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.title.ifBlank { "任务详情" },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleStatusMenu) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(
                        expanded = uiState.showStatusMenu,
                        onDismissRequest = onToggleStatusMenu,
                    ) {
                        TaskStatus.entries.forEach { status ->
                            val label = when (status) {
                                TaskStatus.INBOX -> "收件箱"
                                TaskStatus.PLANNED -> "已规划"
                                TaskStatus.DOING -> "进行中"
                                TaskStatus.BLOCKED -> "已阻塞"
                                TaskStatus.DONE -> "已完成"
                                TaskStatus.DROPPED -> "已放弃"
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { onStatusChange(status) },
                                enabled = status != uiState.status,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onReplan,
                        enabled = !uiState.isReplanning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (uiState.isReplanning) "规划中..." else "重新规划")
                    }
                    if (uiState.status != TaskStatus.DONE) {
                        Button(
                            onClick = { onStatusChange(TaskStatus.DOING) },
                            enabled = uiState.status != TaskStatus.DOING,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("开始执行")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            uiState.error != null -> ErrorState(
                message = uiState.error,
                onRetry = null,
                modifier = Modifier.padding(innerPadding),
            )
            uiState.title.isBlank() && !uiState.isLoading -> EmptyState(
                message = "未找到任务",
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Basic info card
                    item(key = "basic_info") {
                        BasicInfoCard(uiState = uiState)
                    }

                    // AI summary card
                    if (uiState.summary.isNotBlank()) {
                        item(key = "ai_summary") {
                            AiSummaryCard(
                                summary = uiState.summary,
                                nextAction = uiState.nextAction,
                            )
                        }
                    }

                    // Steps card
                    if (uiState.steps.isNotEmpty()) {
                        item(key = "steps_header") {
                            MemoCard(title = "执行步骤") {
                                Column {
                                    uiState.steps.sortedBy { it.stepIndex }.forEach { step ->
                                        StepItem(
                                            title = "${step.stepIndex + 1}. ${step.title}",
                                            description = step.description,
                                            status = step.status,
                                            onStatusToggle = { newStatus ->
                                                onStepStatusChange(step.id, newStatus)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Schedule blocks card
                    if (uiState.scheduleBlocks.isNotEmpty()) {
                        item(key = "schedule_header") {
                            Text(
                                text = "时间安排",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(
                            items = uiState.scheduleBlocks,
                            key = { it.id },
                        ) { block ->
                            TimeBlockCard(
                                startTime = block.startTime,
                                endTime = block.endTime,
                                taskTitle = uiState.title,
                                reason = block.reason,
                                accepted = block.accepted,
                                onAccept = { onScheduleAccepted(block.id) },
                            )
                        }
                    }

                    // Prep items card
                    if (uiState.prepItems.isNotEmpty()) {
                        item(key = "prep_items") {
                            MemoCard(title = "准备事项") {
                                Column {
                                    uiState.prepItems.forEach { prep ->
                                        PrepItem(
                                            content = prep.content,
                                            status = prep.status,
                                            onStatusToggle = { newStatus ->
                                                onPrepStatusChange(prep.id, newStatus)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Risks card
                    if (uiState.risks.isNotEmpty()) {
                        item(key = "risks") {
                            MemoCard(title = "风险提示") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    uiState.risks.forEach { risk ->
                                        Text(
                                            text = "⚠ $risk",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom spacer
                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicInfoCard(
    uiState: TaskDetailUiState,
    modifier: Modifier = Modifier,
) {
    MemoCard(title = "基础信息", modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(status = uiState.status)
            RiskBadge(riskLevel = uiState.riskLevel)
            uiState.priority?.let { PriorityIndicator(priority = it) }
        }

        if (uiState.originalText.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.originalText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        uiState.deadlineAt?.let { deadline ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "截止: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(deadline))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        uiState.estimatedMinutes?.let { minutes ->
            Text(
                text = "预计时长: ${minutes}分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (uiState.imageUris.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ImageAttachmentRow(
                imageUris = uiState.imageUris,
                onAddImage = {},
                onRemoveImage = {},
            )
        }
    }
}

@Composable
private fun AiSummaryCard(
    summary: String,
    nextAction: String,
    modifier: Modifier = Modifier,
) {
    MemoCard(title = "AI 摘要", modifier = modifier) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (nextAction.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "下一步: $nextAction",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
