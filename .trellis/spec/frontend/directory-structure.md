# Directory Structure

> Jetpack Compose UI 层的目录组织规范。

---

## Overview

本项目 UI 层使用 Jetpack Compose + Material 3，单 Activity 架构，Compose Navigation 管理路由。每个页面由 Screen (Composable) + ViewModel 组成。

---

## Directory Layout

```
app/src/main/java/com/memorandum/
├── MainActivity.kt
├── MemorandumApp.kt
│
├── navigation/
│   ├── AppNavGraph.kt            # Top-level NavHost
│   ├── BottomNavBar.kt           # Bottom navigation bar
│   └── Routes.kt                 # Route definitions (sealed class)
│
├── ui/
│   ├── today/
│   │   ├── TodayScreen.kt
│   │   └── TodayViewModel.kt
│   │
│   ├── entry/
│   │   ├── EntryScreen.kt
│   │   └── EntryViewModel.kt
│   │
│   ├── tasks/
│   │   ├── TasksScreen.kt
│   │   └── TasksViewModel.kt
│   │
│   ├── taskdetail/
│   │   ├── TaskDetailScreen.kt
│   │   └── TaskDetailViewModel.kt
│   │
│   ├── memory/
│   │   ├── MemoryScreen.kt
│   │   └── MemoryViewModel.kt
│   │
│   ├── notifications/
│   │   ├── NotificationsScreen.kt
│   │   └── NotificationsViewModel.kt
│   │
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   ├── SettingsViewModel.kt
│   │   ├── ModelConfigScreen.kt
│   │   ├── ModelConfigViewModel.kt
│   │   ├── McpConfigScreen.kt
│   │   ├── McpConfigViewModel.kt
│   │   └── McpCallHistoryScreen.kt
│   │
│   ├── onboarding/
│   │   ├── OnboardingScreen.kt
│   │   └── OnboardingViewModel.kt
│   │
│   ├── common/
│   │   ├── MemoCard.kt
│   │   ├── StatusChip.kt
│   │   ├── RiskBadge.kt
│   │   ├── PriorityIndicator.kt
│   │   ├── TimeBlockCard.kt
│   │   ├── StepItem.kt
│   │   ├── PrepItem.kt
│   │   ├── EmptyState.kt
│   │   ├── LoadingState.kt
│   │   ├── ErrorState.kt
│   │   ├── EntryTypeSelector.kt
│   │   ├── ImageAttachmentRow.kt
│   │   ├── ConfirmDialog.kt
│   │   └── DateTimePicker.kt
│   │
│   └── theme/
│       ├── Theme.kt
│       ├── Color.kt
│       ├── Type.kt
│       └── Shape.kt
│
└── res/
    ├── values/
    │   ├── strings.xml
    │   ├── colors.xml
    │   └── themes.xml
    └── drawable/
```

---

## Module Organization

### Rules for Adding New Pages

1. Create directory under `ui/{page-name}/`
2. Add `{PageName}Screen.kt` (Composable) + `{PageName}ViewModel.kt`
3. Register route in `navigation/Routes.kt`
4. Add navigation entry in `navigation/AppNavGraph.kt`
5. If it's a bottom tab, add to `navigation/BottomNavBar.kt`

### Rules for Adding New Components

- Reusable across pages → `ui/common/`
- Page-specific → keep in the page's own directory
- If a page-specific component grows to 100+ lines, extract to its own file within the page directory

---

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Screen | `{PageName}Screen.kt` | `TodayScreen.kt` |
| ViewModel | `{PageName}ViewModel.kt` | `TodayViewModel.kt` |
| UiState | `{PageName}UiState` (inside ViewModel file or separate) | `TodayUiState` |
| Common component | PascalCase, descriptive | `StatusChip.kt` |
| Theme file | Lowercase | `Theme.kt`, `Color.kt` |
| Route | PascalCase object | `Route.Today`, `Route.TaskDetail` |

---

## Forbidden Patterns

- **NEVER** access DAO or Room Database from UI layer — go through ViewModel → UseCase → Repository
- **NEVER** put business logic in Composable functions — delegate to ViewModel
- **NEVER** create ViewModel instances manually — use Hilt `@HiltViewModel` + `hiltViewModel()`
- **NEVER** use `mutableStateOf` for complex state — use `StateFlow` in ViewModel
- **NEVER** put navigation logic inside common components — pass callbacks up
