package com.memorandum.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorandum.ai.orchestrator.PlanningOrchestrator
import com.memorandum.data.local.room.dao.ScheduleBlockDao
import com.memorandum.data.local.room.enums.PlanningState
import com.memorandum.data.local.room.enums.TaskStatus
import com.memorandum.data.repository.EntryRepository
import com.memorandum.data.repository.NotificationRepository
import com.memorandum.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val scheduleBlockDao: ScheduleBlockDao,
    private val notificationRepository: NotificationRepository,
    private val entryRepository: EntryRepository,
    private val planningOrchestrator: PlanningOrchestrator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        TodayUiState(
            currentDate = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINESE).format(Date()),
        ),
    )
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        loadTodayData()
    }

    private fun loadTodayData() {
        _uiState.update { it.copy(isLoading = true) }
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        viewModelScope.launch {
            combine(
                taskRepository.observeActiveTasks(),
                taskRepository.observeDoneTasks(),
                scheduleBlockDao.observeByDate(todayDate),
                notificationRepository.observeAll(),
                entryRepository.observeActivePlanning(),
            ) { tasks, doneTasks, blocks, notifications, planningEntries ->
                // Top recommendation: highest risk active task
                val topTask = tasks
                    .filter { it.status != TaskStatus.DONE && it.status != TaskStatus.DROPPED }
                    .maxByOrNull { it.riskLevel }
                    ?.let { task ->
                        TaskBrief(
                            taskId = task.id,
                            title = task.title,
                            status = task.status,
                            nextAction = task.nextAction,
                            riskLevel = task.riskLevel,
                            deadlineAt = null,
                        )
                    }

                // Risk alerts: tasks with risk level >= 3
                val riskAlerts = tasks
                    .filter { it.riskLevel >= 3 && it.status != TaskStatus.DONE && it.status != TaskStatus.DROPPED }
                    .map { task ->
                        TaskBrief(
                            taskId = task.id,
                            title = task.title,
                            status = task.status,
                            nextAction = task.nextAction,
                            riskLevel = task.riskLevel,
                            deadlineAt = null,
                        )
                    }

                // Today's schedule blocks with task info
                val taskMap = tasks.associateBy { it.id }
                val todayBlocks = blocks.map { block ->
                    val task = taskMap[block.taskId]
                    ScheduleBlockWithTask(
                        blockId = block.id,
                        startTime = block.startTime,
                        endTime = block.endTime,
                        reason = block.reason,
                        accepted = block.accepted,
                        taskTitle = task?.title.orEmpty(),
                        taskStatus = task?.status ?: TaskStatus.INBOX,
                    )
                }

                // Recent notifications (last 10, not dismissed)
                val recentNotifications = notifications
                    .filter { it.dismissedAt == null }
                    .take(10)
                    .map { notif ->
                        NotificationBrief(
                            id = notif.id,
                            title = notif.title,
                            body = notif.body,
                            createdAt = notif.createdAt,
                            taskRef = notif.taskRef,
                        )
                    }

                // Planning progress entries (non-ASKING)
                val progressItems = planningEntries
                    .filter { it.planningState != PlanningState.ASKING }
                    .map { entry ->
                        PlanningProgress(
                            entryId = entry.id,
                            entryText = entry.text.take(50),
                            state = entry.planningState,
                        )
                    }

                // Clarification from ASKING entries
                val clarification = planningEntries
                    .firstOrNull { it.planningState == PlanningState.ASKING && !it.clarificationQuestion.isNullOrBlank() }
                    ?.let { entry ->
                        ClarificationInfo(
                            entryId = entry.id,
                            question = entry.clarificationQuestion!!,
                            reason = "",
                        )
                    }

                // Done tasks
                val doneTaskBriefs = doneTasks.map { task ->
                    TaskBrief(
                        taskId = task.id,
                        title = task.title,
                        status = task.status,
                        nextAction = task.nextAction,
                        riskLevel = task.riskLevel,
                        deadlineAt = null,
                    )
                }

                TodayDataSnapshot(topTask, todayBlocks, riskAlerts, recentNotifications, progressItems, clarification, doneTaskBriefs)
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        topRecommendation = snapshot.topTask,
                        todayBlocks = snapshot.todayBlocks,
                        riskAlerts = snapshot.riskAlerts,
                        planningEntries = snapshot.planningEntries,
                        pendingClarification = snapshot.pendingClarification,
                        recentNotifications = snapshot.recentNotifications,
                        doneTasks = snapshot.doneTasks,
                        error = null,
                    )
                }
            }
        }
    }

    fun onClarificationAnswer(entryId: String, answer: String) {
        viewModelScope.launch {
            entryRepository.saveClarification(entryId, "", answer)
            _uiState.update { it.copy(pendingClarification = null) }
            launch {
                try {
                    planningOrchestrator.continueAfterClarification(entryId, answer)
                } catch (_: Exception) { }
            }
        }
    }

    fun onClarificationSkip(entryId: String) {
        viewModelScope.launch {
            entryRepository.saveClarification(entryId, "", null)
            _uiState.update { it.copy(pendingClarification = null) }
            launch {
                try {
                    planningOrchestrator.continueAfterClarification(entryId, null)
                } catch (_: Exception) { }
            }
        }
    }

    fun onDismissNotification(notificationId: String) {
        _uiState.update { state ->
            state.copy(
                recentNotifications = state.recentNotifications.filter { it.id != notificationId },
            )
        }
        viewModelScope.launch {
            notificationRepository.markDismissed(notificationId)
        }
    }

    fun onRetryPlanning(entryId: String) {
        viewModelScope.launch {
            try {
                entryRepository.updatePlanningState(entryId, PlanningState.NOT_STARTED)
                launch {
                    try {
                        planningOrchestrator.startPlanning(entryId)
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }
    }

    fun onDeleteEntry(entryId: String) {
        viewModelScope.launch {
            try {
                entryRepository.delete(entryId)
            } catch (_: Exception) { }
        }
    }
}

private data class TodayDataSnapshot(
    val topTask: TaskBrief?,
    val todayBlocks: List<ScheduleBlockWithTask>,
    val riskAlerts: List<TaskBrief>,
    val recentNotifications: List<NotificationBrief>,
    val planningEntries: List<PlanningProgress>,
    val pendingClarification: ClarificationInfo?,
    val doneTasks: List<TaskBrief>,
)

data class TodayUiState(
    val currentDate: String = "",
    val topRecommendation: TaskBrief? = null,
    val todayBlocks: List<ScheduleBlockWithTask> = emptyList(),
    val riskAlerts: List<TaskBrief> = emptyList(),
    val planningEntries: List<PlanningProgress> = emptyList(),
    val pendingClarification: ClarificationInfo? = null,
    val recentNotifications: List<NotificationBrief> = emptyList(),
    val doneTasks: List<TaskBrief> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class TaskBrief(
    val taskId: String,
    val title: String,
    val status: TaskStatus,
    val nextAction: String?,
    val riskLevel: Int,
    val deadlineAt: Long?,
)

data class ScheduleBlockWithTask(
    val blockId: String,
    val startTime: String,
    val endTime: String,
    val reason: String,
    val accepted: Boolean,
    val taskTitle: String,
    val taskStatus: TaskStatus,
)

data class ClarificationInfo(
    val entryId: String,
    val question: String,
    val reason: String,
)

data class NotificationBrief(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val taskRef: String?,
)

data class PlanningProgress(
    val entryId: String,
    val entryText: String,
    val state: PlanningState,
)
