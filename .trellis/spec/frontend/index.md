# Frontend Development Guidelines

> Jetpack Compose UI layer conventions for Memorandum.

---

## Overview

This project is an Android-native app using **Jetpack Compose** + **Material 3**. "Frontend" refers to the UI layer: Composable screens, ViewModels, navigation, and shared UI components.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Compose UI directory layout and page organization | Done |
| [Component Guidelines](./component-guidelines.md) | Composable patterns, parameters, accessibility | Done |
| [Hook Guidelines](./hook-guidelines.md) | ViewModel patterns, side effects, data flow | Done |
| [State Management](./state-management.md) | StateFlow, UiState, state hoisting | Done |
| [Quality Guidelines](./quality-guidelines.md) | Forbidden patterns, testing, performance | Done |
| [Type Safety](./type-safety.md) | Kotlin types, enums, sealed interfaces, serialization | Done |

---

## Pre-Development Checklist

Before writing any UI code, read:

1. **Always**: [Directory Structure](./directory-structure.md) — know where files go
2. **Always**: [Component Guidelines](./component-guidelines.md) — Screen/Content split, modifier rules
3. **Always**: [State Management](./state-management.md) — ViewModel + StateFlow pattern
4. **If adding new screen**: [Hook Guidelines](./hook-guidelines.md) — ViewModel structure, side effects
5. **If defining types**: [Type Safety](./type-safety.md) — enum, sealed interface, serialization rules
6. **Before PR**: [Quality Guidelines](./quality-guidelines.md) — review checklist

---

## Quick Reference

### Screen Structure

```
{PageName}Screen.kt  →  receives ViewModel, collects state
{PageName}Content()   →  pure UI, receives UiState + callbacks, has @Preview
{PageName}ViewModel   →  single UiState StateFlow, event methods
```

### Key Rules

- `modifier: Modifier = Modifier` on all public Composables
- `collectAsStateWithLifecycle()` to collect StateFlow
- `key` parameter in all `LazyColumn` items
- Content descriptions on all interactive icons
- Loading / Empty / Error states on every screen
- No business logic in Composable functions
