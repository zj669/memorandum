package com.memorandum.ui.taskdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorandum.ai.orchestrator.PlanningOrchestrator
import com.memorandum.data.local.room.dao.PlanStepDao
import com.memorandum.data.local.room.dao.PrepItemDao
import com.memorandum.data.local.room.dao.ScheduleBlockDao
import com.memorandum.data.local.room.enums.EntryType
import com.memorandum.data.local.room.enums.PrepStatus
import com.memorandum.data.local.room.enums.StepStatus
import com.memorandum.data.local.room.enums.TaskStatus
import com.memorandum.data.repository.EntryRepository
import com.memorandum.data.repository.TaskRepository
import com.memorandum.domain.usecase.memory.RecordTaskEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val entryRepository: EntryRepository,
    private val planStepDao: PlanStepDao,
    private val scheduleBlockDao: ScheduleBlockDao,
    private val prepItemDao: PrepItemDao,
    private val planningOrchestrator: PlanningOrchestrator,
    private val recordTaskEventUseCase: RecordTaskEventUseCase,
) : ViewModel() {
    val taskId: String = savedStateHandle.get<String>("taskId")
        ?: error("TaskDetailViewModel requires taskId argument")

    private val _uiState = MutableStateFlow(TaskDetailUiState(taskId = taskId))
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    init {
        loadTaskDetail()
    }

    private fun loadTaskDetail() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val taskFlow = taskRepository.observeById(taskId).filterNotNull()
            val entryFlow = taskFlow.flatMapLatest { task ->
                entryRepository.observeById(task.entryId)
            }
            val stepsFlow = planStepDao.observeByTask(taskId)
            val blocksFlow = scheduleBlockDao.observeByTask(taskId)
            val prepsFlow = prepItemDao.observeByTask(taskId)

            combine(
                taskFlow,
                entryFlow,
                stepsFlow,
                blocksFlow,
                prepsFlow,
            ) { task, entry, steps, blocks, preps ->
                TaskDetailUiState(
                    taskId = taskId,
                    entryType = entry?.type ?: EntryType.TASK,
                    title = task.title,
                    status = task.status,
                    summary = task.summary,
                    nextAction = task.nextAction.orEmpty(),
                    riskLevel = task.riskLevel,
                    originalText = entry?.text.orEmpty(),
                    deadlineAt = entry?.deadlineAt,
                    priority = entry?.priority,
                    estimatedMinutes = entry?.estimatedMinutes,
                    imageUris = entry?.imageUrisJson.orEmpty(),
                    steps = steps.map { step ->
                        PlanStepDisplay(
                            id = step.id,
                            stepIndex = step.stepIndex,
                            title = step.title,
                            description = step.description,
                            status = step.status,
                        )
                    },
                    scheduleBlocks = blocks.map { block ->
                        ScheduleBlockDisplay(
                            id = block.id,
                            startTime = block.startTime,
                            endTime = block.endTime,
                            reason = block.reason,
                            accepted = block.accepted,
                        )
                    },
                    prepItems = preps.map { prep ->
                        PrepItemDisplay(
                            id = prep.id,
                            content = prep.content,
                            status = prep.status,
                        )
                    },
                    isLoading = false,
                )
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onStatusChange(newStatus: TaskStatus) {
        val previousStatus = _uiState.value.status
        _uiState.update { it.copy(status = newStatus, showStatusMenu = false) }
        viewModelScope.launch {
            taskRepository.updateStatus(taskId, newStatus)
            recordTaskEventUseCase.record(taskId, "STATUS_CHANGE", "${previousStatus.name} -> ${newStatus.name}")
            if (newStatus == TaskStatus.DONE) {
                recordTaskEventUseCase.record(taskId, "DONE")
            }
        }
    }

    fun onToggleStatusMenu() {
        _uiState.update { it.copy(showStatusMenu = !it.showStatusMenu) }
    }

    fun onStepStatusChange(stepId: String, newStatus: StepStatus) {
        _uiState.update { state ->
            state.copy(
                steps = state.steps.map { step ->
                    if (step.id == stepId) step.copy(status = newStatus) else step
                },
            )
        }
        viewModelScope.launch {
            planStepDao.updateStatus(stepId, newStatus, System.currentTimeMillis())
            recordTaskEventUseCase.record(taskId, "STEP_STATUS_CHANGE", "$stepId:${newStatus.name}")
        }
    }

    fun onPrepStatusChange(prepId: String, newStatus: PrepStatus) {
        _uiState.update { state ->
            state.copy(
                prepItems = state.prepItems.map { prep ->
                    if (prep.id == prepId) prep.copy(status = newStatus) else prep
                },
            )
        }
        viewModelScope.launch {
            prepItemDao.updateStatus(prepId, newStatus)
        }
    }

    fun onScheduleAccepted(blockId: String) {
        _uiState.update { state ->
            state.copy(
                scheduleBlocks = state.scheduleBlocks.map { block ->
                    if (block.id == blockId) block.copy(accepted = true) else block
                },
            )
        }
        viewModelScope.launch {
            scheduleBlockDao.accept(blockId)
            recordTaskEventUseCase.record(taskId, "ACCEPTED_PLAN", blockId)
        }
    }

    fun onReplan() {
        _uiState.update { it.copy(isReplanning = true) }
        viewModelScope.launch {
            try {
                planningOrchestrator.replan(taskId)
            } catch (_: Exception) { }
            _uiState.update { it.copy(isReplanning = false) }
        }
    }
}

data class TaskDetailUiState(
    val taskId: String = "",
    val entryType: EntryType = EntryType.TASK,
    val title: String = "",
    val status: TaskStatus = TaskStatus.INBOX,
    val summary: String = "",
    val nextAction: String = "",
    val riskLevel: Int = 0,
    val originalText: String = "",
    val deadlineAt: Long? = null,
    val priority: Int? = null,
    val estimatedMinutes: Int? = null,
    val imageUris: List<String> = emptyList(),
    val steps: List<PlanStepDisplay> = emptyList(),
    val scheduleBlocks: List<ScheduleBlockDisplay> = emptyList(),
    val prepItems: List<PrepItemDisplay> = emptyList(),
    val risks: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isReplanning: Boolean = false,
    val showStatusMenu: Boolean = false,
    val error: String? = null,
)

data class PlanStepDisplay(
    val id: String,
    val stepIndex: Int,
    val title: String,
    val description: String,
    val status: StepStatus,
)

data class ScheduleBlockDisplay(
    val id: String,
    val startTime: String,
    val endTime: String,
    val reason: String,
    val accepted: Boolean,
)

data class PrepItemDisplay(
    val id: String,
    val content: String,
    val status: PrepStatus,
)
