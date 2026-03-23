package com.memorandum.ui.today

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorandum.data.local.room.enums.PlanningState
import com.memorandum.ui.common.ConfirmDialog
import com.memorandum.ui.common.EmptyState
import com.memorandum.ui.common.ErrorState
import com.memorandum.ui.common.LoadingState
import com.memorandum.ui.common.MemoCard
import com.memorandum.ui.common.RiskBadge
import com.memorandum.ui.common.StatusChip
import com.memorandum.ui.common.TimeBlockCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        onClarificationAnswer = viewModel::onClarificationAnswer,
        onClarificationSkip = viewModel::onClarificationSkip,
        onRetryPlanning = viewModel::onRetryPlanning,
        onDeleteEntry = viewModel::onDeleteEntry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayContent(
    uiState: TodayUiState,
    onNavigateToEntry: () -> Unit,
    onNavigateToTask: (String) -> Unit,
    onClarificationAnswer: (String, String) -> Unit,
    onClarificationSkip: (String) -> Unit,
    onRetryPlanning: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClarificationSheet by remember { mutableStateOf(false) }
    var deleteTargetEntryId by remember { mutableStateOf<String?>(null) }

    // Delete confirmation dialog
    deleteTargetEntryId?.let { entryId ->
        ConfirmDialog(
            title = "删除条目",
            message = "确定要删除这个条目吗？此操作不可撤销。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                onDeleteEntry(entryId)
                deleteTargetEntryId = null
            },
            onDismiss = { deleteTargetEntryId = null },
        )
    }

    // Auto-show clarification sheet when a new clarification arrives
    LaunchedEffect(uiState.pendingClarification) {
        if (uiState.pendingClarification != null) {
            showClarificationSheet = true
        }
    }

    // Show sheet when there's a pending clarification
    if (uiState.pendingClarification != null && showClarificationSheet) {
        ClarificationSheet(
            info = uiState.pendingClarification,
            onAnswer = { answer ->
                onClarificationAnswer(uiState.pendingClarification.entryId, answer)
                showClarificationSheet = false
            },
            onSkip = {
                onClarificationSkip(uiState.pendingClarification.entryId)
                showClarificationSheet = false
            },
            onDismiss = { showClarificationSheet = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.currentDate,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToEntry,
                modifier = Modifier.testTag("today_fab"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建条目")
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
            else -> {
                val hasContent = uiState.topRecommendation != null ||
                    uiState.todayBlocks.isNotEmpty() ||
                    uiState.riskAlerts.isNotEmpty() ||
                    uiState.planningEntries.isNotEmpty() ||
                    uiState.pendingClarification != null ||
                    uiState.recentNotifications.isNotEmpty() ||
                    uiState.doneTasks.isNotEmpty()

                if (!hasContent) {
                    EmptyState(
                        message = "今天还没有安排，点击右下角添加新条目",
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Planning progress - split active vs failed
                        val activePlanning = uiState.planningEntries.filter { it.state != PlanningState.FAILED }
                        val failedPlanning = uiState.planningEntries.filter { it.state == PlanningState.FAILED }

                        if (activePlanning.isNotEmpty()) {
                            item(key = "section_planning") {
                                Text(
                                    text = "规划中",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            items(
                                items = activePlanning,
                                key = { "planning_${it.entryId}" },
                            ) { progress ->
                                PlanningProgressCard(progress = progress)
                            }
                        }

                        if (failedPlanning.isNotEmpty()) {
                            item(key = "section_failed") {
                                Text(
                                    text = "规划失败",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            items(
                                items = failedPlanning,
                                key = { "failed_${it.entryId}" },
                            ) { progress ->
                                FailedPlanningCard(
                                    progress = progress,
                                    onRetry = { onRetryPlanning(progress.entryId) },
                                    onDelete = { deleteTargetEntryId = progress.entryId },
                                )
                            }
                        }

                        // Top recommendation
                        uiState.topRecommendation?.let { rec ->
                            item(key = "recommendation") {
                                TopRecommendationCard(
                                    task = rec,
                                    onClick = { onNavigateToTask(rec.taskId) },
                                    modifier = Modifier.testTag("today_recommendation_card"),
                                )
                            }
                        }

                        // Today schedule
                        if (uiState.todayBlocks.isNotEmpty()) {
                            item(key = "section_today") {
                                Text(
                                    text = "今日安排",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            items(
                                items = uiState.todayBlocks,
                                key = { it.blockId },
                            ) { block ->
                                TimeBlockCard(
                                    startTime = block.startTime,
                                    endTime = block.endTime,
                                    taskTitle = block.taskTitle,
                                    reason = block.reason,
                                    accepted = block.accepted,
                                )
                            }
                        }

                        // Risk alerts
                        if (uiState.riskAlerts.isNotEmpty()) {
                            item(key = "section_risk") {
                                Text(
                                    text = "风险提醒",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            items(
                                items = uiState.riskAlerts,
                                key = { "risk_${it.taskId}" },
                            ) { alert ->
                                RiskAlertCard(
                                    task = alert,
                                    onClick = { onNavigateToTask(alert.taskId) },
                                )
                            }
                        }

                        // Pending clarification
                        uiState.pendingClarification?.let { clarification ->
                            item(key = "section_clarification") {
                                Text(
                                    text = "待回答",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            item(key = "clarification") {
                                ClarificationCard(
                                    info = clarification,
                                    onClick = { showClarificationSheet = true },
                                )
                            }
                        }

                        // Recent notifications
                        if (uiState.recentNotifications.isNotEmpty()) {
                            item(key = "section_notifications") {
                                Text(
                                    text = "最近通知",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            items(
                                items = uiState.recentNotifications,
                                key = { it.id },
                            ) { notification ->
                                NotificationBriefCard(
                                    notification = notification,
                                    onClick = {
                                        notification.taskRef?.let { onNavigateToTask(it) }
                                    },
                                )
                            }
                        }

                        // Done tasks (collapsible)
                        if (uiState.doneTasks.isNotEmpty()) {
                            item(key = "section_done") {
                                var expanded by remember { mutableStateOf(false) }
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expanded = !expanded }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                            contentDescription = if (expanded) "收起" else "展开",
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "已完成 ${uiState.doneTasks.size} 项",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (expanded) {
                                        uiState.doneTasks.forEach { task ->
                                            DoneTaskCard(
                                                task = task,
                                                onClick = { onNavigateToTask(task.taskId) },
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanningProgressCard(
    progress: PlanningProgress,
    modifier: Modifier = Modifier,
) {
    MemoCard(modifier = modifier) {
        Text(
            text = progress.entryText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = progress.state.toDisplayLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun PlanningState.toDisplayLabel(): String = when (this) {
    PlanningState.CLARIFYING -> "正在分析..."
    PlanningState.PLANNING -> "正在规划..."
    PlanningState.ENRICHING_MCP -> "正在搜索补充信息..."
    PlanningState.SAVING -> "正在保存..."
    else -> "处理中..."
}

@Composable
private fun FailedPlanningCard(
    progress: PlanningProgress,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MemoCard(modifier = modifier) {
        Text(
            text = progress.entryText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "规划失败",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) {
                Text("重试")
            }
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TopRecommendationCard(
    task: TaskBrief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MemoCard(
        title = "推荐优先处理",
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(status = task.status)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "查看详情",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        task.nextAction?.let { action ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "下一步: $action",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (task.riskLevel > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            RiskBadge(riskLevel = task.riskLevel)
        }
    }
}

@Composable
private fun RiskAlertCard(
    task: TaskBrief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MemoCard(modifier = modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                task.deadlineAt?.let { deadline ->
                    Text(
                        text = "截止: ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(deadline))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            RiskBadge(riskLevel = task.riskLevel)
        }
    }
}

@Composable
private fun ClarificationCard(
    info: ClarificationInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MemoCard(modifier = modifier.clickable(onClick = onClick)) {
        Text(
            text = info.question,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = info.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击回答 →",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun NotificationBriefCard(
    notification: NotificationBrief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MemoCard(modifier = modifier.clickable(onClick = onClick)) {
        Text(
            text = notification.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = notification.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notification.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClarificationSheet(
    info: ClarificationInfo,
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    var answerText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = info.question,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = info.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = answerText,
                onValueChange = { answerText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入回答...") },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onSkip) {
                    Text("跳过")
                }
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = { onAnswer(answerText) },
                    enabled = answerText.isNotBlank(),
                ) {
                    Text("提交")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DoneTaskCard(
    task: TaskBrief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MemoCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .alpha(0.6f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
