package com.memorandum.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorandum.data.local.room.enums.TaskStatus
import com.memorandum.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    // Cache all tasks from the repository for local filter/sort/search
    private var allTasks: List<TaskListItem> = emptyList()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            taskRepository.observeAll()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { taskEntities ->
                    allTasks = taskEntities.map { task ->
                        TaskListItem(
                            id = task.id,
                            title = task.title,
                            status = task.status,
                            riskLevel = task.riskLevel,
                            nextAction = task.nextAction,
                            deadlineAt = null,
                            planReady = task.planReady,
                            lastProgressAt = task.lastProgressAt,
                        )
                    }
                    applyFilterSortSearch()
                }
        }
    }

    fun onFilterChanged(filter: TaskStatusFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        applyFilterSortSearch()
    }

    fun onSortChanged(sort: TaskSortBy) {
        _uiState.update { it.copy(sortBy = sort) }
        applyFilterSortSearch()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilterSortSearch()
    }

    fun onToggleSearch() {
        _uiState.update { it.copy(isSearchVisible = !it.isSearchVisible, searchQuery = "") }
        applyFilterSortSearch()
    }

    private fun applyFilterSortSearch() {
        val state = _uiState.value

        // Filter
        val filtered = when (state.selectedFilter) {
            TaskStatusFilter.ACTIVE -> allTasks.filter {
                it.status != TaskStatus.DONE && it.status != TaskStatus.DROPPED
            }
            TaskStatusFilter.INBOX -> allTasks.filter { it.status == TaskStatus.INBOX }
            TaskStatusFilter.PLANNED -> allTasks.filter { it.status == TaskStatus.PLANNED }
            TaskStatusFilter.DOING -> allTasks.filter { it.status == TaskStatus.DOING }
            TaskStatusFilter.BLOCKED -> allTasks.filter { it.status == TaskStatus.BLOCKED }
            TaskStatusFilter.DONE -> allTasks.filter { it.status == TaskStatus.DONE }
            TaskStatusFilter.ALL -> allTasks
        }

        // Search
        val searched = if (state.searchQuery.isBlank()) {
            filtered
        } else {
            val query = state.searchQuery.lowercase()
            filtered.filter { task ->
                task.title.lowercase().contains(query)
                    || task.nextAction?.lowercase()?.contains(query) == true
            }
        }

        // Sort
        val sorted = when (state.sortBy) {
            TaskSortBy.UPDATED -> searched.sortedByDescending { it.lastProgressAt ?: 0L }
            TaskSortBy.DEADLINE -> searched.sortedBy { it.deadlineAt ?: Long.MAX_VALUE }
            TaskSortBy.RISK -> searched.sortedByDescending { it.riskLevel }
        }

        _uiState.update { it.copy(isLoading = false, tasks = sorted, error = null) }
    }
}

data class TasksUiState(
    val selectedFilter: TaskStatusFilter = TaskStatusFilter.ACTIVE,
    val sortBy: TaskSortBy = TaskSortBy.UPDATED,
    val searchQuery: String = "",
    val tasks: List<TaskListItem> = emptyList(),
    val isSearchVisible: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

enum class TaskStatusFilter { ACTIVE, INBOX, PLANNED, DOING, BLOCKED, DONE, ALL }

enum class TaskSortBy { UPDATED, DEADLINE, RISK }

data class TaskListItem(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val riskLevel: Int,
    val nextAction: String?,
    val deadlineAt: Long?,
    val planReady: Boolean,
    val lastProgressAt: Long?,
)
