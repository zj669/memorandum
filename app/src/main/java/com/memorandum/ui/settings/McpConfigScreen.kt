package com.memorandum.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorandum.ui.theme.MemorandumTheme

@Composable
fun McpConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: McpConfigViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            onNavigateBack()
        }
    }

    McpConfigContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNameChanged = viewModel::onNameChanged,
        onBaseUrlChanged = viewModel::onBaseUrlChanged,
        onAuthTypeChanged = viewModel::onAuthTypeChanged,
        onAuthValueChanged = viewModel::onAuthValueChanged,
        onToolWhitelistChanged = viewModel::onToolWhitelistChanged,
        onTestConnection = viewModel::onTestConnection,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun McpConfigContent(
    uiState: McpConfigUiState,
    onNavigateBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onAuthTypeChanged: (AuthType) -> Unit,
    onAuthValueChanged: (String) -> Unit,
    onToolWhitelistChanged: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.isEditMode) "编辑 MCP 服务" else "添加 MCP 服务")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (uiState.isEditMode) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("mcp_config_delete"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除配置",
                                tint = MaterialTheme.colorScheme.error,
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Service name
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                label = { Text("服务名称") },
                placeholder = { Text("例如: 搜索服务") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mcp_config_name"),
            )

            // Base URL
            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text("Base URL") },
                placeholder = { Text("https://mcp.example.com") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mcp_config_url"),
            )

            // Auth type selector
            Text(
                text = "认证方式",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AuthType.entries.forEach { type ->
                    FilterChip(
                        selected = uiState.authType == type,
                        onClick = { onAuthTypeChanged(type) },
                        label = {
                            Text(
                                when (type) {
                                    AuthType.NONE -> "无认证"
                                    AuthType.BEARER -> "Bearer Token"
                                    AuthType.HEADER -> "自定义 Header"
                                },
                            )
                        },
                        modifier = Modifier.testTag("mcp_config_auth_${type.name}"),
                    )
                }
            }

            // Auth value (shown when auth type != NONE)
            if (uiState.authType != AuthType.NONE) {
                OutlinedTextField(
                    value = uiState.authValue,
                    onValueChange = onAuthValueChanged,
                    label = {
                        Text(
                            when (uiState.authType) {
                                AuthType.BEARER -> "Bearer Token"
                                AuthType.HEADER -> "Header (格式: Name: Value)"
                                AuthType.NONE -> ""
                            },
                        )
                    },
                    placeholder = {
                        Text(
                            when (uiState.authType) {
                                AuthType.BEARER -> "your-token-here"
                                AuthType.HEADER -> "X-Api-Key: your-key"
                                AuthType.NONE -> ""
                            },
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (uiState.authType == AuthType.BEARER) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_config_auth_value"),
                )
            }

            // Tool whitelist
            OutlinedTextField(
                value = uiState.toolWhitelist,
                onValueChange = onToolWhitelistChanged,
                label = { Text("工具白名单（可选）") },
                placeholder = { Text("search, fetch, summarize") },
                singleLine = false,
                minLines = 2,
                supportingText = {
                    Text("逗号分隔，留空表示允许所有工具")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mcp_config_whitelist"),
            )

            // Test connection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = uiState.isValid && !uiState.isTesting,
                    modifier = Modifier.testTag("mcp_config_test"),
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(18.dp)
                                .width(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("测试连接")
                }

                when (val result = uiState.testResult) {
                    is McpTestResult.Success -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "连接成功",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "可用工具: ${result.tools.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    is McpTestResult.Failed -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "连接失败",
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = result.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    null -> { /* no result yet */ }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = onSave,
                enabled = uiState.isValid && !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mcp_config_save"),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .width(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.isEditMode) "保存修改" else "添加服务")
            }

            // Delete button (edit mode)
            if (uiState.isEditMode) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_config_delete_bottom"),
                ) {
                    Text("删除此服务")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Previews ──

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun McpConfigContentNewPreview() {
    MemorandumTheme {
        McpConfigContent(
            uiState = McpConfigUiState(
                name = "搜索服务",
                baseUrl = "https://mcp.example.com",
                authType = AuthType.BEARER,
                authValue = "token123",
                isValid = true,
            ),
            onNavigateBack = {},
            onNameChanged = {},
            onBaseUrlChanged = {},
            onAuthTypeChanged = {},
            onAuthValueChanged = {},
            onToolWhitelistChanged = {},
            onTestConnection = {},
            onSave = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun McpConfigContentEditPreview() {
    MemorandumTheme {
        McpConfigContent(
            uiState = McpConfigUiState(
                isEditMode = true,
                serverId = "1",
                name = "搜索服务",
                baseUrl = "https://mcp.example.com",
                authType = AuthType.NONE,
                isValid = true,
                testResult = McpTestResult.Success(listOf("search", "fetch", "summarize")),
            ),
            onNavigateBack = {},
            onNameChanged = {},
            onBaseUrlChanged = {},
            onAuthTypeChanged = {},
            onAuthValueChanged = {},
            onToolWhitelistChanged = {},
            onTestConnection = {},
            onSave = {},
            onDelete = {},
        )
    }
}
