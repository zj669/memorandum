package com.memorandum.ui.settings

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorandum.ui.common.ConfirmDialog
import com.memorandum.ui.common.ErrorState
import com.memorandum.ui.theme.MemorandumTheme

@Composable
fun SettingsScreen(
    onNavigateToNotifications: () -> Unit,
    onNavigateToModelConfig: (String?) -> Unit,
    onNavigateToMcpConfig: (String?) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionStatus()
    }

    SettingsContent(
        uiState = uiState,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToModelConfig = onNavigateToModelConfig,
        onNavigateToMcpConfig = onNavigateToMcpConfig,
        onToggleMcpServer = viewModel::onToggleMcpServer,
        onHeartbeatFrequencyChanged = viewModel::onHeartbeatFrequencyChanged,
        onQuietHoursStartChanged = viewModel::onQuietHoursStartChanged,
        onQuietHoursEndChanged = viewModel::onQuietHoursEndChanged,
        onAllowNetworkChanged = viewModel::onAllowNetworkChanged,
        onOpenNotificationSettings = {
            viewModel.openNotificationSettings(context)
            viewModel.refreshPermissionStatus()
        },
        onOpenExactAlarmSettings = {
            viewModel.openExactAlarmSettings(context)
            viewModel.refreshPermissionStatus()
        },
        onRefreshPermissions = viewModel::refreshPermissionStatus,
        onShowClearMemoryDialog = viewModel::onShowClearMemoryDialog,
        onDismissClearMemoryDialog = viewModel::onDismissClearMemoryDialog,
        onConfirmClearMemory = viewModel::onConfirmClearMemory,
        onShowClearNotificationsDialog = viewModel::onShowClearNotificationsDialog,
        onDismissClearNotificationsDialog = viewModel::onDismissClearNotificationsDialog,
        onConfirmClearNotifications = viewModel::onConfirmClearNotifications,
        onShowClearAllDialog = viewModel::onShowClearAllDialog,
        onDismissClearAllDialog = viewModel::onDismissClearAllDialog,
        onConfirmClearAll = viewModel::onConfirmClearAll,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onNavigateToNotifications: () -> Unit,
    onNavigateToModelConfig: (String?) -> Unit,
    onNavigateToMcpConfig: (String?) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onHeartbeatFrequencyChanged: (String) -> Unit,
    onQuietHoursStartChanged: (String) -> Unit,
    onQuietHoursEndChanged: (String) -> Unit,
    onAllowNetworkChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onShowClearMemoryDialog: () -> Unit,
    onDismissClearMemoryDialog: () -> Unit,
    onConfirmClearMemory: () -> Unit,
    onShowClearNotificationsDialog: () -> Unit,
    onDismissClearNotificationsDialog: () -> Unit,
    onConfirmClearNotifications: () -> Unit,
    onShowClearAllDialog: () -> Unit,
    onDismissClearAllDialog: () -> Unit,
    onConfirmClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingQuietHoursField by rememberSaveable { mutableStateOf<String?>(null) }
    var draftQuietHours by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("设置") })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("settings_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "section_ai_model") {
                SectionHeader(title = "AI 模型")
            }
            items(items = uiState.llmConfigs, key = { it.id }) { config ->
                LlmConfigCard(
                    config = config,
                    onClick = { onNavigateToModelConfig(config.id) },
                )
            }
            item(key = "add_model") {
                AddButton(
                    text = "添加模型",
                    onClick = { onNavigateToModelConfig(null) },
                    modifier = Modifier.testTag("settings_add_model"),
                )
            }

            item(key = "section_mcp") {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "MCP 服务")
            }
            items(items = uiState.mcpServers, key = { it.id }) { server ->
                McpServerCard(
                    server = server,
                    onToggle = { enabled -> onToggleMcpServer(server.id, enabled) },
                    onClick = { onNavigateToMcpConfig(server.id) },
                )
            }
            item(key = "add_mcp") {
                AddButton(
                    text = "添加服务",
                    onClick = { onNavigateToMcpConfig(null) },
                    modifier = Modifier.testTag("settings_add_mcp"),
                )
            }

            item(key = "section_heartbeat") {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "心跳与通知")
            }
            item(key = "heartbeat_frequency") {
                HeartbeatFrequencySelector(
                    selected = uiState.heartbeatFrequency,
                    onSelected = onHeartbeatFrequencyChanged,
                )
            }
            item(key = "quiet_hours") {
                QuietHoursRow(
                    start = uiState.quietHoursStart,
                    end = uiState.quietHoursEnd,
                    onStartClick = {
                        editingQuietHoursField = "start"
                        draftQuietHours = uiState.quietHoursStart
                    },
                    onEndClick = {
                        editingQuietHoursField = "end"
                        draftQuietHours = uiState.quietHoursEnd
                    },
                )
            }
            item(key = "notification_permission") {
                PermissionStatusRow(
                    title = "通知权限",
                    description = if (uiState.notificationPermissionGranted) "已授权，可正常显示系统通知" else "未授权，心跳与提醒将无法送达",
                    granted = uiState.notificationPermissionGranted,
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    actionLabel = "去授权",
                    onAction = onOpenNotificationSettings,
                )
            }
            item(key = "exact_alarm_permission") {
                PermissionStatusRow(
                    title = "精确闹钟权限",
                    description = if (uiState.exactAlarmPermissionGranted) "已授权，可注册精确到时提醒" else "未授权，到时提醒与稍后提醒无法精确恢复",
                    granted = uiState.exactAlarmPermissionGranted,
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    actionLabel = "去设置",
                    onAction = onOpenExactAlarmSettings,
                )
            }
            item(key = "open_notifications") {
                AddButton(
                    text = "查看通知历史",
                    onClick = onNavigateToNotifications,
                    modifier = Modifier.testTag("settings_notifications_history"),
                )
            }
            item(key = "refresh_permissions") {
                TextButton(
                    onClick = onRefreshPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_refresh_permissions"),
                ) {
                    Text("刷新权限状态")
                }
            }

            item(key = "settings_error") {
                uiState.error?.let { error ->
                    ErrorState(message = error, onRetry = null)
                }
            }

            item(key = "section_privacy") {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "隐私")
            }
            item(key = "network_toggle") {
                SettingsToggleRow(
                    title = "允许联网",
                    subtitle = "关闭后 AI 将不会调用 MCP 搜索",
                    checked = uiState.allowNetworkAccess,
                    onCheckedChange = onAllowNetworkChanged,
                    modifier = Modifier.testTag("settings_network_toggle"),
                )
            }

            item(key = "section_data") {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "数据管理")
            }
            item(key = "clear_memory") {
                DangerActionRow(
                    text = "清除记忆数据",
                    onClick = onShowClearMemoryDialog,
                    modifier = Modifier.testTag("settings_clear_memory"),
                )
            }
            item(key = "clear_notifications") {
                DangerActionRow(
                    text = "清除通知历史",
                    onClick = onShowClearNotificationsDialog,
                    modifier = Modifier.testTag("settings_clear_notifications"),
                )
            }
            item(key = "clear_all") {
                DangerActionRow(
                    text = "清除所有数据",
                    onClick = onShowClearAllDialog,
                    isDestructive = true,
                    modifier = Modifier.testTag("settings_clear_all"),
                )
            }
        }
    }

    if (editingQuietHoursField != null) {
        TimeEditDialog(
            title = if (editingQuietHoursField == "start") "设置静默开始时间" else "设置静默结束时间",
            value = draftQuietHours,
            onValueChange = { draftQuietHours = it },
            onConfirm = {
                when (editingQuietHoursField) {
                    "start" -> onQuietHoursStartChanged(draftQuietHours)
                    "end" -> onQuietHoursEndChanged(draftQuietHours)
                }
                editingQuietHoursField = null
            },
            onDismiss = { editingQuietHoursField = null },
        )
    }

    if (uiState.showClearMemoryDialog) {
        ConfirmDialog(
            title = "清除记忆数据",
            message = "将删除所有 AI 记忆和用户画像数据，此操作不可撤销。",
            confirmText = "清除",
            isDestructive = true,
            onConfirm = onConfirmClearMemory,
            onDismiss = onDismissClearMemoryDialog,
        )
    }
    if (uiState.showClearNotificationsDialog) {
        ConfirmDialog(
            title = "清除通知历史",
            message = "将删除所有通知记录和心跳日志，此操作不可撤销。",
            confirmText = "清除",
            isDestructive = true,
            onConfirm = onConfirmClearNotifications,
            onDismiss = onDismissClearNotificationsDialog,
        )
    }
    if (uiState.showClearAllDialog) {
        ConfirmDialog(
            title = "清除所有数据",
            message = "将删除所有数据并重置应用，包括模型配置、MCP 服务、记忆、通知等。此操作不可撤销！",
            confirmText = "全部清除",
            isDestructive = true,
            onConfirm = onConfirmClearAll,
            onDismiss = onDismissClearAllDialog,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun LlmConfigCard(
    config: LlmConfigDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = config.providerName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = config.modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (config.supportsImage) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "支持图片",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun McpServerCard(
    server: McpServerDisplay,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = server.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = server.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Switch(checked = server.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun AddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeartbeatFrequencySelector(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf("LOW" to "低", "MEDIUM" to "中", "HIGH" to "高")
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "心跳频率", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("settings_heartbeat_$value"),
                )
            }
        }
    }
}

@Composable
private fun QuietHoursRow(
    start: String,
    end: String,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "静默时段", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "在此时段内不会发送通知",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onStartClick) {
                Text(
                    text = start,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(text = "至", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onEndClick) {
                Text(
                    text = end,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun TimeEditDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text("时间（HH:mm）") },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun PermissionStatusRow(
    title: String,
    description: String,
    granted: Boolean,
    icon: @Composable () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (granted) "已授权" else "未授权",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DangerActionRow(
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsContentPreview() {
    MemorandumTheme {
        SettingsContent(
            uiState = SettingsUiState(
                llmConfigs = listOf(
                    LlmConfigDisplay("1", "OpenAI", "gpt-4o", true),
                    LlmConfigDisplay("2", "DeepSeek", "deepseek-chat", false),
                ),
                mcpServers = listOf(
                    McpServerDisplay("1", "搜索服务", "https://mcp.example.com", true),
                ),
                notificationPermissionGranted = false,
                exactAlarmPermissionGranted = true,
            ),
            onNavigateToNotifications = {},
            onNavigateToModelConfig = {},
            onNavigateToMcpConfig = {},
            onToggleMcpServer = { _, _ -> },
            onHeartbeatFrequencyChanged = {},
            onQuietHoursStartChanged = {},
            onQuietHoursEndChanged = {},
            onAllowNetworkChanged = {},
            onOpenNotificationSettings = {},
            onOpenExactAlarmSettings = {},
            onRefreshPermissions = {},
            onShowClearMemoryDialog = {},
            onDismissClearMemoryDialog = {},
            onConfirmClearMemory = {},
            onShowClearNotificationsDialog = {},
            onDismissClearNotificationsDialog = {},
            onConfirmClearNotifications = {},
            onShowClearAllDialog = {},
            onDismissClearAllDialog = {},
            onConfirmClearAll = {},
        )
    }
}
