package com.memorandum.ui.notifications

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorandum.data.local.room.enums.NotificationType
import com.memorandum.ui.common.EmptyState
import com.memorandum.ui.common.ErrorState
import com.memorandum.ui.common.LoadingState
import com.memorandum.ui.common.MemoCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNotificationClick = viewModel::onNotificationClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsContent(
    uiState: NotificationsUiState,
    onNavigateBack: () -> Unit,
    onNotificationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("通知历史") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            uiState.error != null -> ErrorState(
                message = uiState.error,
                onRetry = null,
                modifier = Modifier.padding(innerPadding),
            )
            uiState.notifications.isEmpty() -> EmptyState(
                message = "暂无通知",
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = uiState.notifications,
                        key = { it.id },
                    ) { notification ->
                        NotificationCard(
                            notification = notification,
                            onClick = { onNotificationClick(notification.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationDisplayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, iconTint) = when (notification.type) {
        NotificationType.PLAN_READY -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        NotificationType.TIME_TO_START -> Icons.Default.AccessTime to MaterialTheme.colorScheme.tertiary
        NotificationType.DEADLINE_RISK -> Icons.Default.Warning to MaterialTheme.colorScheme.error
        NotificationType.PREP_NEEDED -> Icons.Default.Notifications to MaterialTheme.colorScheme.secondary
        NotificationType.STALE_TASK -> Icons.Default.Warning to MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        NotificationType.HEARTBEAT_CHECK -> Icons.Default.Notifications to MaterialTheme.colorScheme.outline
    }

    val isUnread = notification.status == NotificationStatus.UNREAD

    MemoCard(modifier = modifier.clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                            .format(Date(notification.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    val statusLabel = when (notification.status) {
                        NotificationStatus.UNREAD -> "未读"
                        NotificationStatus.CLICKED -> "已读"
                        NotificationStatus.DISMISSED -> "已忽略"
                        NotificationStatus.SNOOZED -> "已延后"
                    }
                    val statusColor = when (notification.status) {
                        NotificationStatus.UNREAD -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                    notification.taskRef?.let {
                        Text(
                            text = "关联任务 →",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
