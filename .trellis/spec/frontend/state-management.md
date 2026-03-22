# State Management

> State management conventions for Memorandum's Compose UI layer.

---

## Overview

- Local UI state: `remember { mutableStateOf() }` — for transient UI-only state
- Screen state: `ViewModel` + `StateFlow` — for data-driven state
- Persistent state: Room (structured data) + DataStore (preferences)
- No global state management library (no Redux/MobX equivalent needed)

---

## State Categories

### 1. Transient UI State (Composable-level)

State that only matters for current UI interaction, lost on configuration change:

```kotlin
// Text field input buffer (before submission)
var textInput by remember { mutableStateOf("") }

// Bottom sheet expanded/collapsed
var showSheet by rememberSaveable { mutableStateOf(false) }

// Dropdown menu open/closed
var menuExpanded by remember { mutableStateOf(false) }
```

**Use `remember`** for: animation state, focus state, scroll position, temporary UI toggles.

**Use `rememberSaveable`** for: form input that should survive rotation.

### 2. Screen State (ViewModel-level)

State derived from data sources, survives configuration change:

```kotlin
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()
}
```

**Use ViewModel StateFlow** for: list data, filter/sort state, loading/error state, form validation results.

### 3. Persistent State (Room / DataStore)

State that survives app restart:

| Storage | What |
|---------|------|
| Room | Entries, tasks, plans, memories, notifications, configs |
| DataStore | Heartbeat frequency, quiet hours, network toggle, onboarding flag |

---

## State Flow Architecture

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│   Room DB   │────→│  Repository  │────→│  ViewModel   │
│  DataStore  │     │   (Flow)     │     │ (StateFlow)  │
└─────────────┘     └──────────────┘     └──────┬───────┘
                                                │
                                    collectAsStateWithLifecycle()
                                                │
                                         ┌──────▼───────┐
                                         │   Screen     │
                                         │ (Composable) │
                                         └──────────────┘
```

### Data Flow Rules

1. **Unidirectional**: Data flows down (State → UI), events flow up (UI → ViewModel)
2. **Single source of truth**: Room/DataStore is the source, ViewModel is the projection
3. **No direct DB access from UI**: always go through ViewModel → Repository
4. **Combine flows in ViewModel**: merge multiple data sources into single UiState

---

## Combining Multiple Flows

```kotlin
private fun observeData() {
    viewModelScope.launch {
        combine(
            taskRepository.observeTasksForDate(today),
            scheduleBlockDao.observeByDate(today),
            heartbeatLogDao.observeLatest()
        ) { tasks, blocks, heartbeat ->
            TodayUiState(
                todayTasks = tasks.map { it.toListItem() },
                scheduleBlocks = blocks,
                lastHeartbeat = heartbeat?.toStatus(),
                isLoading = false
            )
        }.catch { e ->
            _uiState.update { it.copy(error = e.message, isLoading = false) }
        }.collect { state ->
            _uiState.value = state
        }
    }
}
```

---

## Derived State

Use `derivedStateOf` for computed values in Composable:

```kotlin
val filteredTasks by remember(uiState.tasks, uiState.selectedFilter) {
    derivedStateOf {
        when (uiState.selectedFilter) {
            TaskStatusFilter.ACTIVE -> uiState.tasks.filter { it.status !in terminalStatuses }
            TaskStatusFilter.DONE -> uiState.tasks.filter { it.status == TaskStatus.DONE }
            else -> uiState.tasks
        }
    }
}
```

For heavy computation, prefer doing it in ViewModel instead.

---

## State Hoisting

### Pattern

```kotlin
// Parent owns state
@Composable
fun EntryScreen(viewModel: EntryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    EntryTypeSelector(
        selected = uiState.selectedType,
        onSelect = viewModel::onTypeSelected
    )
}

// Child is stateless
@Composable
fun EntryTypeSelector(
    selected: EntryType,
    onSelect: (EntryType) -> Unit,
    modifier: Modifier = Modifier
)
```

### Rules

- Common components are always stateless (receive state + callbacks)
- Screen-level Composable connects ViewModel to stateless content
- Only use local `remember` state for truly transient UI concerns

---

## Common Mistakes

- **Storing list data in `remember`** → should be in ViewModel, survives config change
- **Multiple `MutableStateFlow` in ViewModel** → combine into single UiState
- **Collecting Flow in Composable without lifecycle** → use `collectAsStateWithLifecycle()`
- **Mutating state directly in Composable** → always go through ViewModel event handler
- **Using DataStore for structured data** → use Room; DataStore is for simple key-value preferences
- **Forgetting `catch` on Flow collection** → uncaught exception crashes the app
