# Type Safety

> Kotlin type safety conventions for Memorandum.

---

## Overview

- Language: **Kotlin** (strong static typing)
- Serialization: **kotlinx.serialization** (compile-time safe)
- No `Any`, no unchecked casts, no reflection-based serialization
- Enums for fixed domain values, sealed interfaces for polymorphic types

---

## Type Organization

### Where Types Live

| Type Category | Location | Example |
|---------------|----------|---------|
| Room Entity | `data/local/room/entity/` | `TaskEntity.kt` |
| Domain Enum | `data/local/room/enums/` | `TaskStatus.kt`, `EntryType.kt` |
| AI Output Schema | `ai/schema/` | `PlannerOutput.kt` |
| UI Display Model | Inside ViewModel file or `ui/{page}/` | `TaskListItem`, `TodayUiState` |
| Repository Interface | `data/repository/` | `TaskRepository.kt` |

### Rules

- **Entity types stay in data layer** — never pass Entity directly to UI
- **UI models are defined per-screen** — map Entity → display model in ViewModel
- **AI schema types are `@Serializable`** — used for JSON parsing only
- **Enums are shared across layers** — defined once in `enums/`

---

## Enum Conventions

### Definition

```kotlin
// data/local/room/enums/TaskStatus.kt
enum class TaskStatus {
    INBOX, PLANNED, DOING, BLOCKED, DONE, DROPPED
}
```

### Rules

- All domain enums are defined in `data/local/room/enums/`
- Enum values are UPPER_SNAKE_CASE
- Room stores enums as TEXT (enum name) — no custom TypeConverter needed
- AI output uses string matching: `TaskStatus.valueOf(output.status)`
- Always handle unknown enum values from AI output:

```kotlin
val status = try {
    TaskStatus.valueOf(rawStatus)
} catch (_: IllegalArgumentException) {
    Log.w(TAG, "Unknown status from AI: $rawStatus, defaulting to INBOX")
    TaskStatus.INBOX
}
```

---

## Sealed Interface Conventions

### For Result Types

```kotlin
sealed interface PlanningResult {
    data class NeedsClarification(val question: String, val reason: String) : PlanningResult
    data class Success(val taskId: String) : PlanningResult
    data class Failed(val error: String) : PlanningResult
}
```

### For Error Types

```kotlin
sealed interface AiError {
    data class NetworkError(val cause: Throwable) : AiError
    data class AuthError(val message: String) : AiError
    data class ParseError(val rawResponse: String, val cause: Throwable) : AiError
}
```

### Rules

- Prefer `sealed interface` over `sealed class` (more flexible)
- Each variant is a `data class` (for equality and toString)
- Use `when` expression for exhaustive handling (compiler enforces all branches)

---

## Serialization

### AI Output Schema

```kotlin
@Serializable
data class PlannerOutput(
    @SerialName("needs_clarification") val needsClarification: Boolean,
    @SerialName("task_title") val taskTitle: String? = null,
    val steps: List<PlanStep> = emptyList(),
    // ...
)
```

### Rules

- All AI output types use `@Serializable` (kotlinx.serialization)
- Use `@SerialName` for snake_case JSON keys
- All fields have defaults (AI may omit optional fields)
- Parse with `ignoreUnknownKeys = true`:

```kotlin
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
```

- Never use Gson or Moshi — standardize on kotlinx.serialization

---

## Null Safety

### Rules

```kotlin
// Nullable when data may legitimately be absent
val deadlineAt: Long?          // User may not set deadline
val goalId: String?            // Task may not belong to a goal

// Non-null when data is always present
val id: String                 // UUID, always exists
val status: TaskStatus         // Always has a status
val createdAt: Long            // Always has creation time
```

- **Never use `!!`** — use `?.let`, `?:`, or `requireNotNull` with message
- **Default values in data classes** — prefer defaults over nullable when sensible
- **`requireNotNull` for programmer errors** — when null indicates a bug:

```kotlin
val taskId: String = savedStateHandle.get<String>("taskId")
    ?: error("TaskDetailViewModel requires taskId argument")
```

---

## Entity ↔ Display Model Mapping

```kotlin
// In ViewModel or mapping extension
fun TaskEntity.toListItem() = TaskListItem(
    id = id,
    title = title,
    status = status,
    riskLevel = riskLevel,
    nextAction = nextAction,
    deadlineAt = deadlineAt,
    planReady = planReady
)
```

### Rules

- Mapping functions are extension functions on Entity
- Defined in ViewModel file or a `Mappers.kt` file in the page directory
- Never expose Entity to Composable — always map to display model

---

## Forbidden Patterns

| Pattern | Why | Instead |
|---------|-----|---------|
| `Any` as parameter/return type | No type safety | Use specific type or generic |
| `as` unchecked cast | Runtime crash risk | Use `as?` with null check |
| `@Suppress("UNCHECKED_CAST")` | Hides real issues | Fix the type |
| Gson / Moshi | Inconsistent with project | Use kotlinx.serialization |
| `HashMap` / `ArrayList` | Mutable by default | Use `Map` / `List` (immutable interfaces) |
| String constants for enum values | Typo-prone, no exhaustive check | Use enum class |
| `Pair` / `Triple` for domain data | Unreadable | Use named data class |
