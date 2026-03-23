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
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorandum.ui.common.ConfirmDialog
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
            // ── AI 模型 Section ──
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

            // ── MCP 服务 Section ──
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

            // ── 心跳与通知 Section ──
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
                    onStartChanged = onQuietHoursStartChanged,
                    onEndChanged = onQuietHoursEndChanged,
                )
            }

            // ── 隐私 Section ──
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

            // ── 数据管理 Section ──
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

    // Dialogs
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

// ── Section Components ──

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
                Text(
                    text = config.providerName,
                    style = MaterialTheme.typography.bodyLarge,
                )
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
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = server.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Switch(
                checked = server.enabled,
                onCheckedChange = onToggle,
            )
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
        Text(
            text = "心跳频率",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "静默时段",
            style = MaterialTheme.typography.bodyMedium,
        )
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
            // DataStore connected; consider upgrading to Material3 TimePicker in a future UI pass
            Text(
                text = start,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = "至", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = end,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
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
            tint = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

// ── Previews ──

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
            ),
            onNavigateToNotifications = {},
            onNavigateToModelConfig = {},
            onNavigateToMcpConfig = {},
            onToggleMcpServer = { _, _ -> },
            onHeartbeatFrequencyChanged = {},
            onQuietHoursStartChanged = {},
            onQuietHoursEndChanged = {},
            onAllowNetworkChanged = {},
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
