# Hook Guidelines → ViewModel & Side Effect Patterns

> This project uses Jetpack Compose (not React). This file covers ViewModel patterns and Compose side effects, which serve the same role as "hooks" in React.

---

## Overview

- State management: **ViewModel** + **StateFlow**
- Side effects: Compose effect APIs (`LaunchedEffect`, `DisposableEffect`, etc.)
- Lifecycle: `collectAsStateWithLifecycle()` from `lifecycle-runtime-compose`
- DI: Hilt `@HiltViewModel` + `hiltViewModel()`

---

## ViewModel Patterns

### Standard ViewModel Structure

```kotlin
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        // Combine multiple flows into UiState
        viewModelScope.launch {
            combine(
                taskRepository.observeActiveTasks(),
                notificationRepository.observeRecent()
            ) { tasks, notifications ->
                TodayUiState(
                    tasks = tasks,
                    notifications = notifications,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onTaskClick(taskId: String) { /* navigation event */ }
    fun onStatusChange(taskId: String, status: TaskStatus) {
        viewModelScope.launch {
            taskRepository.updateStatus(taskId, status)
        }
    }
}
```

### Rules

1. **One ViewModel per Screen** — never share ViewModel between screens
2. **Single UiState** — one `data class` holds all screen state
3. **Expose `StateFlow`** — never expose `MutableStateFlow`
4. **Use `viewModelScope`** — never create custom CoroutineScope
5. **Event methods are `fun`** — not `suspend fun` (launch internally)

---

## UiState Design

### Pattern

```kotlin
data class TasksUiState(
    val tasks: List<TaskListItem> = emptyList(),
    val selectedFilter: TaskStatusFilter = TaskStatusFilter.ACTIVE,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)
```

### Rules

- Default values for all fields (empty state is valid)
- `isLoading = true` initially, set to `false` when data arrives
- `error: String? = null` for error state
- Use domain-specific display models (`TaskListItem`), not raw Entities
- Sealed interface for complex result states:

```kotlin
sealed interface SaveResult {
    data class Success(val id: String) : SaveResult
    data class Error(val message: String) : SaveResult
}
```

---

## One-Time Events

For navigation, snackbar, toast — use `Channel` + `Flow`:

```kotlin
@HiltViewModel
class EntryViewModel @Inject constructor(...) : ViewModel() {

    private val _events = Channel<EntryEvent>(Channel.BUFFERED)
    val events: Flow<EntryEvent> = _events.receiveAsFlow()

    fun onSubmit() {
        viewModelScope.launch {
            val result = entryRepository.create(...)
            _events.send(EntryEvent.SaveSuccess(result))
        }
    }
}

sealed interface EntryEvent {
    data class SaveSuccess(val entryId: String) : EntryEvent
    data class SaveError(val message: String) : EntryEvent
}
```

Consume in Screen:

```kotlin
@Composable
fun EntryScreen(viewModel: EntryViewModel = hiltViewModel(), onNavigateBack: () -> Unit) {
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EntryEvent.SaveSuccess -> onNavigateBack()
                is EntryEvent.SaveError -> { /* show snackbar */ }
            }
        }
    }
}
```

---

## Compose Side Effects

### LaunchedEffect

```kotlin
// Run once when screen appears
LaunchedEffect(Unit) {
    viewModel.loadInitialData()
}

// Re-run when key changes
LaunchedEffect(taskId) {
    viewModel.loadTask(taskId)
}
```

### DisposableEffect

```kotlin
// Cleanup when leaving composition
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event -> ... }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

### rememberCoroutineScope

```kotlin
// For event-driven coroutines (button clicks in Composable)
val scope = rememberCoroutineScope()
Button(onClick = {
    scope.launch { scaffoldState.snackbarHostState.showSnackbar("Done") }
})
```

### Rules

- **Prefer ViewModel** for data loading — use `LaunchedEffect` only for UI-specific side effects
- **Never call `suspend` functions directly in Composable** — use `LaunchedEffect` or `rememberCoroutineScope`
- **Use `Unit` key** for one-time effects, specific key for reactive effects
- **Always clean up** in `DisposableEffect.onDispose`

---

## Data Flow Summary

```
Room DB → Flow → Repository → Flow → ViewModel (combine) → StateFlow → Screen (collectAsStateWithLifecycle)
                                                                      ↓
User Action → Screen callback → ViewModel fun → UseCase (suspend) → Repository → Room DB
```

---

## Common Mistakes

- **Using `collectAsState()` instead of `collectAsStateWithLifecycle()`** → doesn't pause collection when app is backgrounded
- **Creating coroutines in Composable without `LaunchedEffect`** → runs on every recomposition
- **Exposing `MutableStateFlow` from ViewModel** → UI can accidentally mutate state
- **Multiple UiState flows in one ViewModel** → hard to maintain, use single combined UiState
- **Forgetting `Channel.BUFFERED`** → events may be dropped
- **Using `remember { mutableStateOf() }` for complex state** → should be in ViewModel for lifecycle survival
