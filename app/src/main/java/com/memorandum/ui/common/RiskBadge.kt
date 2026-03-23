package com.memorandum.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun RiskBadge(
    riskLevel: Int,
    modifier: Modifier = Modifier,
) {
    if (riskLevel <= 0) return

    val (label, color) = when (riskLevel) {
        1 -> "低风险" to MaterialTheme.colorScheme.tertiary
        2 -> "中风险" to MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else -> "高风险" to MaterialTheme.colorScheme.error
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
