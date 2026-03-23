package com.memorandum.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PriorityIndicator(
    priority: Int,
    modifier: Modifier = Modifier,
) {
    val color = when {
        priority >= 5 -> MaterialTheme.colorScheme.error
        priority >= 4 -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        priority >= 3 -> MaterialTheme.colorScheme.tertiary
        priority >= 2 -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    Row(modifier = modifier) {
        repeat(priority.coerceIn(1, 5)) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = "优先级 $priority",
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
