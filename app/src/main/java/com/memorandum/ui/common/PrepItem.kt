package com.memorandum.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.memorandum.data.local.room.enums.PrepStatus

@Composable
fun PrepItem(
    content: String,
    status: PrepStatus,
    onStatusToggle: (PrepStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val checked = status == PrepStatus.DONE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { isChecked ->
                onStatusToggle(if (isChecked) PrepStatus.DONE else PrepStatus.TODO)
            },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            color = if (checked) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}
