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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorandum.ui.theme.MemorandumTheme

@Composable
fun ModelConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelConfigViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            onNavigateBack()
        }
    }

    ModelConfigContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onProviderNameChanged = viewModel::onProviderNameChanged,
        onBaseUrlChanged = viewModel::onBaseUrlChanged,
        onModelNameChanged = viewModel::onModelNameChanged,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onSupportsImageChanged = viewModel::onSupportsImageChanged,
        onPresetSelected = viewModel::onPresetSelected,
        onTestConnection = viewModel::onTestConnection,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelConfigContent(
    uiState: ModelConfigUiState,
    onNavigateBack: () -> Unit,
    onProviderNameChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelNameChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onSupportsImageChanged: (Boolean) -> Unit,
    onPresetSelected: (LlmPreset) -> Unit,
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
                    Text(if (uiState.isEditMode) "编辑模型" else "添加模型")
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
                            modifier = Modifier.testTag("model_config_delete"),
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
            // Preset selector
            if (!uiState.isEditMode) {
                Text(
                    text = "快速选择",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LlmPresets.presets.forEach { preset ->
                        FilterChip(
                            selected = uiState.providerName == preset.name,
                            onClick = { onPresetSelected(preset) },
                            label = { Text(preset.name) },
                        )
                    }
                }
            }

            // Provider name
            OutlinedTextField(
                value = uiState.providerName,
                onValueChange = onProviderNameChanged,
                label = { Text("服务商名称") },
                placeholder = { Text("例如: OpenAI") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_config_provider"),
            )

            // Base URL
            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_config_url"),
            )

            // Model name
            OutlinedTextField(
                value = uiState.modelName,
                onValueChange = onModelNameChanged,
                label = { Text("模型名称") },
                placeholder = { Text("例如: gpt-4o") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_config_model"),
            )

            // Suggested models chips (when preset has suggestions)
            val currentPreset = LlmPresets.presets.find { it.name == uiState.providerName }
            if (currentPreset != null && currentPreset.suggestedModels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    currentPreset.suggestedModels.forEach { model ->
                        FilterChip(
                            selected = uiState.modelName == model,
                            onClick = { onModelNameChanged(model) },
                            label = { Text(model) },
                        )
                    }
                }
            }

            // API Key
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_config_apikey"),
            )

            // Supports image toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "支持图片输入",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "启用后可发送图片给模型分析",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.supportsImage,
                    onCheckedChange = onSupportsImageChanged,
                )
            }

            // Test connection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = uiState.isValid && !uiState.isTesting,
                    modifier = Modifier.testTag("model_config_test"),
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

                // Test result
                when (val result = uiState.testResult) {
                    is ConnectionTestResult.Success -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "连接成功",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = result.responsePreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is ConnectionTestResult.Failed -> {
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
                    .testTag("model_config_save"),
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
                Text(if (uiState.isEditMode) "保存修改" else "添加模型")
            }

            // Delete button (edit mode, bottom of form)
            if (uiState.isEditMode) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_config_delete_bottom"),
                ) {
                    Text("删除此配置")
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
private fun ModelConfigContentNewPreview() {
    MemorandumTheme {
        ModelConfigContent(
            uiState = ModelConfigUiState(
                providerName = "OpenAI",
                baseUrl = "https://api.openai.com",
                modelName = "gpt-4o",
                supportsImage = true,
            ),
            onNavigateBack = {},
            onProviderNameChanged = {},
            onBaseUrlChanged = {},
            onModelNameChanged = {},
            onApiKeyChanged = {},
            onSupportsImageChanged = {},
            onPresetSelected = {},
            onTestConnection = {},
            onSave = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelConfigContentEditPreview() {
    MemorandumTheme {
        ModelConfigContent(
            uiState = ModelConfigUiState(
                isEditMode = true,
                configId = "1",
                providerName = "DeepSeek",
                baseUrl = "https://api.deepseek.com",
                modelName = "deepseek-chat",
                apiKey = "sk-test",
                isValid = true,
                testResult = ConnectionTestResult.Success("连接成功"),
            ),
            onNavigateBack = {},
            onProviderNameChanged = {},
            onBaseUrlChanged = {},
            onModelNameChanged = {},
            onApiKeyChanged = {},
            onSupportsImageChanged = {},
            onPresetSelected = {},
            onTestConnection = {},
            onSave = {},
            onDelete = {},
        )
    }
}
