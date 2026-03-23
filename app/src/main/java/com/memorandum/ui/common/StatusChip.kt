package com.memorandum.ui.common

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.memorandum.data.local.room.enums.TaskStatus

@Composable
fun StatusChip(
    status: TaskStatus,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (status) {
        TaskStatus.INBOX -> "收件箱" to MaterialTheme.colorScheme.outline
        TaskStatus.PLANNED -> "已规划" to MaterialTheme.colorScheme.primary
        TaskStatus.DOING -> "进行中" to MaterialTheme.colorScheme.tertiary
        TaskStatus.BLOCKED -> "已阻塞" to MaterialTheme.colorScheme.error
        TaskStatus.DONE -> "已完成" to MaterialTheme.colorScheme.secondary
        TaskStatus.DROPPED -> "已放弃" to MaterialTheme.colorScheme.error
    }
    FilterChip(
        selected = true,
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.15f),
            selectedLabelColor = color,
        ),
    )
}
