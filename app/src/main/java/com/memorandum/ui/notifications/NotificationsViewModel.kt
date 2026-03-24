package com.memorandum.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorandum.data.local.room.enums.NotificationType
import com.memorandum.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            notificationRepository.observeAll()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { notifications ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            notifications = notifications.map { notif ->
                                val status = when {
                                    notif.deliveryFailedAt != null -> NotificationStatus.DELIVERY_FAILED
                                    notif.snoozedUntil != null -> NotificationStatus.SNOOZED
                                    notif.dismissedAt != null -> NotificationStatus.DISMISSED
                                    notif.clickedAt != null -> NotificationStatus.CLICKED
                                    else -> NotificationStatus.UNREAD
                                }
                                NotificationDisplayItem(
                                    id = notif.id,
                                    type = notif.type,
                                    title = notif.title,
                                    body = notif.body,
                                    taskRef = notif.taskRef,
                                    createdAt = notif.createdAt,
                                    status = status,
                                )
                            },
                            error = null,
                        )
                    }
                }
        }
    }

    fun onNotificationClick(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markClicked(notificationId)
        }
    }
}

data class NotificationsUiState(
    val notifications: List<NotificationDisplayItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class NotificationDisplayItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val taskRef: String?,
    val createdAt: Long,
    val status: NotificationStatus,
)

enum class NotificationStatus { UNREAD, CLICKED, DISMISSED, SNOOZED, DELIVERY_FAILED }
