# Component Guidelines

> Jetpack Compose component conventions for Memorandum.

---

## Overview

- UI Framework: **Jetpack Compose** with **Material 3**
- Design language: Material You (Dynamic Color on Android 12+)
- Component philosophy: small, focused, reusable Composables
- Accessibility: all interactive elements must have content descriptions

---

## Component Structure

### Standard Screen Pattern

```kotlin
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
    onNavigateToTask: (String) -> Unit,
    onNavigateToEntry: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TodayContent(
        uiState = uiState,
        onTaskClick = onNavigateToTask,
        onCreateEntry = onNavigateToEntry,
        onClarificationAnswer = viewModel::onClarificationAnswer,
        onClarificationSkip = viewModel::onClarificationSkip
    )
}

@Composable
private fun TodayContent(
    uiState: TodayUiState,
    onTaskClick: (String) -> Unit,
    onCreateEntry: () -> Unit,
    onClarificationAnswer: (String, String) -> Unit,
    onClarificationSkip: (String) -> Unit
) {
    // Pure UI, no ViewModel reference
    // Testable with @Preview
}
```

### Rules

1. **Screen Composable**: receives ViewModel, collects state, delegates to Content
2. **Content Composable**: pure UI, receives UiState + callbacks, no ViewModel reference
3. **Split for testability**: Screen handles DI, Content handles rendering
4. **Preview**: always add `@Preview` on Content composable

---

## Parameter Conventions

### Ordering

```kotlin
@Composable
fun MemoCard(
    // 1. Required data
    title: String,
    subtitle: String?,
    // 2. Modifier (always present, always has default)
    modifier: Modifier = Modifier,
    // 3. Optional visual config
    elevation: Dp = 2.dp,
    // 4. Slot content (trailing lambdas)
    leadingIcon: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```

### Rules

- `modifier: Modifier = Modifier` is REQUIRED on all public Composables
- Modifier is always the first optional parameter (after required data)
- Navigation callbacks: `on{Action}: (params) -> Unit`
- Event callbacks: `on{Event}: () -> Unit`
- Never pass ViewModel as parameter — pass state and callbacks

---

## Common Components

### MemoCard

Base card container used across all pages:

```kotlin
@Composable
fun MemoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
    }
}
```

### StatusChip

```kotlin
@Composable
fun StatusChip(status: TaskStatus, modifier: Modifier = Modifier)
// Maps TaskStatus to color + label
// INBOX=gray, PLANNED=blue, DOING=green, BLOCKED=orange, DONE=teal, DROPPED=red
```

### EmptyState / LoadingState / ErrorState

```kotlin
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier)

@Composable
fun LoadingState(modifier: Modifier = Modifier)

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier)
```

---

## Styling Patterns

### Theme Usage

Always use `MaterialTheme` tokens, never hardcode colors or text styles:

```kotlin
// Good
Text(text = title, style = MaterialTheme.typography.titleMedium)
Surface(color = MaterialTheme.colorScheme.surfaceVariant)

// Bad
Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
Surface(color = Color(0xFFE0E0E0))
```

### Spacing Constants

```kotlin
// Use consistent spacing
val CardSpacing = 12.dp
val CardPadding = 16.dp
val SectionSpacing = 24.dp
val ChipSpacing = 8.dp
```

### Dark Mode

- Use Material 3 dynamic color scheme — automatically handles dark mode
- Never use hardcoded colors that break in dark mode
- Test all screens in both light and dark mode

---

## Accessibility

### Required

- All `IconButton` must have `contentDescription`
- All `Image` must have `contentDescription` (or `null` for decorative)
- All clickable elements must have minimum 48dp touch target
- Use `semantics` block for complex custom components
- Screen readers must be able to navigate all interactive elements

```kotlin
// Good
IconButton(onClick = onClose) {
    Icon(Icons.Default.Close, contentDescription = "关闭")
}

// Bad
IconButton(onClick = onClose) {
    Icon(Icons.Default.Close, contentDescription = null)  // Missing!
}
```

### Testing

- Use `Modifier.testTag("tag")` for UI test targets
- Key interactive elements must have test tags

---

## List Patterns

### LazyColumn

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(CardSpacing)
) {
    // Section header
    item(key = "section_today") {
        Text("今日安排", style = MaterialTheme.typography.titleSmall)
    }

    // List items with stable keys
    items(items = tasks, key = { it.id }) { task ->
        TaskListCard(task = task, onClick = { onTaskClick(task.id) })
    }
}
```

### Rules

- Always provide `key` parameter for list items (use entity ID)
- Use `Arrangement.spacedBy()` for consistent spacing
- Use `contentPadding` instead of wrapping in padding modifier

---

## Common Mistakes

- **Forgetting `collectAsStateWithLifecycle()`** → using `collectAsState()` doesn't respect lifecycle, wastes resources
- **Passing ViewModel to child Composables** → breaks testability and preview, pass state + callbacks instead
- **Missing `modifier` parameter** → parent can't control layout
- **Hardcoding strings** → use `stringResource(R.string.xxx)` for user-visible text
- **Heavy computation in Composable** → use `remember` or `derivedStateOf`, or move to ViewModel
- **Not providing stable keys in LazyColumn** → causes unnecessary recomposition
