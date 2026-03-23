package com.memorandum.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memorandum.data.local.room.enums.EntryType

@Composable
fun EntryTypeSelector(
    selected: EntryType,
    onSelect: (EntryType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val types = listOf(
        EntryType.TASK to "✓ 任务",
        EntryType.GOAL to "\uD83C\uDFAF 目标",
        EntryType.MEMO to "\uD83D\uDCDD 备忘",
        EntryType.REFERENCE to "\uD83D\uDCCE 参考",
        EntryType.REMINDER to "\uD83D\uDD14 提醒",
    )
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        types.forEach { (type, label) ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(label) },
            )
        }
    }
}
