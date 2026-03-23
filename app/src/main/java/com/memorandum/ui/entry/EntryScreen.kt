package com.memorandum.ui.entry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorandum.ui.common.EntryTypeSelector
import com.memorandum.ui.common.ImageAttachmentRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EntryScreen(
    onNavigateBack: () -> Unit,
    viewModel: EntryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saveResult) {
        when (uiState.saveResult) {
            is SaveResult.Success -> {
                viewModel.onSaveResultConsumed()
                onNavigateBack()
            }
            else -> {}
        }
    }

    EntryContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onTypeSelected = viewModel::onTypeSelected,
        onTextChanged = viewModel::onTextChanged,
        onImagesAdded = viewModel::onImagesAdded,
        onImageRemoved = viewModel::onImageRemoved,
        onDeadlineSet = viewModel::onDeadlineSet,
        onPrioritySet = viewModel::onPrioritySet,
        onEstimatedMinutesSet = viewModel::onEstimatedMinutesSet,
        onToggleOptionalFields = viewModel::onToggleOptionalFields,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryContent(
    uiState: EntryUiState,
    onNavigateBack: () -> Unit,
    onTypeSelected: (com.memorandum.data.local.room.enums.EntryType) -> Unit,
    onTextChanged: (String) -> Unit,
    onImagesAdded: (List<String>) -> Unit,
    onImageRemoved: (String) -> Unit,
    onDeadlineSet: (Long?) -> Unit,
    onPrioritySet: (Int?) -> Unit,
    onEstimatedMinutesSet: (Int?) -> Unit,
    onToggleOptionalFields: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var showDatePicker by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImagesAdded(uris.map { it.toString() })
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("新建条目") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onSubmit,
                enabled = uiState.text.isNotBlank() && !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .imePadding()
                    .testTag("entry_submit_button"),
            ) {
                Text(if (uiState.isSaving) "保存中..." else "提交")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            EntryTypeSelector(
                selected = uiState.selectedType,
                onSelect = onTypeSelected,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.text,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("输入内容...") },
                maxLines = 10,
            )

            Spacer(modifier = Modifier.height(12.dp))

            ImageAttachmentRow(
                imageUris = uiState.imageUris,
                onAddImage = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemoveImage = { uri -> onImageRemoved(uri) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable optional fields
            TextButton(onClick = onToggleOptionalFields) {
                Icon(
                    imageVector = if (uiState.showOptionalFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (uiState.showOptionalFields) "收起可选字段" else "展开可选字段",
                )
                Text(if (uiState.showOptionalFields) "收起可选字段" else "更多选项")
            }

            AnimatedVisibility(visible = uiState.showOptionalFields) {
                Column {
                    // Deadline
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(
                            text = uiState.deadline?.let { ts ->
                                "截止时间: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))}"
                            } ?: "设置截止时间",
                        )
                    }

                    // Priority slider
                    Text(
                        text = "优先级: ${uiState.priority ?: "未设置"}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Slider(
                        value = (uiState.priority ?: 3).toFloat(),
                        onValueChange = { onPrioritySet(it.toInt()) },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Estimated minutes
                    var minutesText by remember(uiState.estimatedMinutes) {
                        mutableStateOf(uiState.estimatedMinutes?.toString() ?: "")
                    }
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { value ->
                            minutesText = value
                            onEstimatedMinutesSet(value.toIntOrNull())
                        },
                        label = { Text("预计时长 (分钟)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            // Error display
            if (uiState.saveResult is SaveResult.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (uiState.saveResult as SaveResult.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.deadline ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDeadlineSet(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDeadlineSet(null)
                    showDatePicker = false
                }) {
                    Text("清除")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
