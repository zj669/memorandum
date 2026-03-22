# Quality Guidelines

> Code quality standards for Memorandum's Compose UI layer.

---

## Overview

- Language: Kotlin 2.0+
- UI: Jetpack Compose + Material 3
- Lint: Android Lint + ktlint
- Testing: Compose UI Test + JUnit
- Accessibility: Material 3 defaults + explicit content descriptions

---

## Forbidden Patterns

| Pattern | Why | Instead |
|---------|-----|---------|
| `collectAsState()` | Doesn't respect lifecycle | `collectAsStateWithLifecycle()` |
| ViewModel as Composable parameter | Breaks testability and preview | Pass UiState + callbacks |
| `mutableStateOf` for data-driven state | Lost on config change | ViewModel + StateFlow |
| Hardcoded color values | Breaks dark mode | `MaterialTheme.colorScheme.*` |
| Hardcoded text strings | Not localizable | `stringResource(R.string.xxx)` |
| `GlobalScope.launch` in ViewModel | Leaks coroutines | `viewModelScope.launch` |
| Business logic in Composable | Untestable, wrong layer | Move to ViewModel or UseCase |
| Direct DAO/Repository access from Composable | Violates architecture | Go through ViewModel |
| `LazyColumn` without `key` | Unnecessary recomposition | Always provide stable `key` |
| Nested scrollable containers | Crashes or broken scroll | Use single `LazyColumn` with sections |

---

## Required Patterns

### Every Screen Must Have

1. **Loading state** — show `LoadingState` composable while data loads
2. **Empty state** — show `EmptyState` when list is empty
3. **Error state** — show `ErrorState` with retry button on failure
4. **Preview** — `@Preview` on Content composable (light + dark)

```kotlin
@Composable
private fun TasksContent(uiState: TasksUiState, ...) {
    when {
        uiState.isLoading -> LoadingState()
        uiState.error != null -> ErrorState(message = uiState.error, onRetry = onRetry)
        uiState.tasks.isEmpty() -> EmptyState(message = "暂无任务")
        else -> TasksList(tasks = uiState.tasks, ...)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TasksContentPreview() {
    MemorandumTheme {
        TasksContent(uiState = TasksUiState(tasks = previewTasks), ...)
    }
}
```

### Every Public Composable Must Have

1. `modifier: Modifier = Modifier` parameter
2. Content description on all interactive icons
3. Minimum 48dp touch target on clickable elements

### Every ViewModel Must Have

1. Single `uiState: StateFlow<XxxUiState>`
2. `init {}` block that starts data observation
3. Event methods as regular `fun` (not `suspend`)

---

## Testing Requirements

### Compose UI Tests

| What | When |
|------|------|
| Screen renders correctly | Every screen |
| User interactions trigger callbacks | Clickable elements |
| State changes reflect in UI | Filter/sort/search |
| Empty/Loading/Error states display | Every screen |

```kotlin
@Test
fun todayScreen_showsLoadingState() {
    composeTestRule.setContent {
        TodayContent(uiState = TodayUiState(isLoading = true), ...)
    }
    composeTestRule.onNodeWithTag("loading").assertIsDisplayed()
}
```

### ViewModel Tests

| What | When |
|------|------|
| Initial state is correct | Every ViewModel |
| Events update state correctly | Every event handler |
| Error handling works | Failure scenarios |

### Test Tags

Use `Modifier.testTag()` for key elements:

```kotlin
// Naming: "{screen}_{element}"
Modifier.testTag("today_recommendation_card")
Modifier.testTag("tasks_filter_active")
Modifier.testTag("entry_submit_button")
```

---

## Performance Guidelines

### Recomposition

- Use `remember` for expensive computations
- Use `derivedStateOf` for filtered/sorted lists
- Use stable types (data class, immutable collections) for parameters
- Avoid creating lambdas in loops — extract to named functions

```kotlin
// Bad: new lambda on every recomposition
items(tasks) { task ->
    TaskCard(onClick = { onTaskClick(task.id) })
}

// Good: stable key + extracted callback
items(tasks, key = { it.id }) { task ->
    TaskCard(onClick = { onTaskClick(task.id) })
}
```

### Image Loading

- Use Coil for async image loading
- Always specify size constraints
- Provide placeholder and error drawables

---

## Spacing & Layout Standards

| Token | Value | Usage |
|-------|-------|-------|
| `CardSpacing` | 12.dp | Between cards in list |
| `CardPadding` | 16.dp | Inside card |
| `SectionSpacing` | 24.dp | Between sections |
| `ChipSpacing` | 8.dp | Between filter chips |
| `ScreenPadding` | 16.dp | Screen horizontal padding |

---

## Code Review Checklist

- [ ] No hardcoded colors or text strings
- [ ] `modifier` parameter present on all public Composables
- [ ] `collectAsStateWithLifecycle()` used (not `collectAsState()`)
- [ ] Content descriptions on all interactive icons
- [ ] `@Preview` present (light + dark)
- [ ] Loading / Empty / Error states handled
- [ ] `key` provided in `LazyColumn` items
- [ ] No business logic in Composable functions
- [ ] ViewModel uses single UiState pattern
- [ ] Test tags on key interactive elements
