# Quality Guidelines

> Code quality standards for Memorandum's data, AI, and scheduler layers.

---

## Overview

- Language: Kotlin 2.0+
- Build: Gradle Kotlin DSL with Version Catalog
- DI: Hilt
- Async: Kotlin Coroutines + Flow
- Serialization: kotlinx.serialization
- Lint: Android Lint + ktlint

---

## Forbidden Patterns

| Pattern | Why | Instead |
|---------|-----|---------|
| `GlobalScope.launch` | Leaks coroutines, no lifecycle awareness | Use `viewModelScope` or structured scope |
| `runBlocking` in production code | Blocks thread, causes ANR | Use `suspend` functions |
| `Thread.sleep()` | Blocks thread | Use `delay()` |
| `!!` (non-null assertion) | Crashes on null | Use `?.let`, `?:`, or `requireNotNull` with message |
| Mutable public state in Repository | Race conditions | Return `Flow` or immutable copies |
| `@Suppress("UNCHECKED_CAST")` | Hides type safety issues | Fix the type properly |
| Raw SQL strings in DAO without `@Query` | No compile-time validation | Always use Room `@Query` |
| `fallbackToDestructiveMigration()` | Destroys user data | Write proper migrations |
| Hardcoded strings in Prompt | Unmaintainable | Use `PromptBuilder` with templates |
| `catch (e: Exception)` at top level | Swallows all errors | Catch specific exceptions |

---

## Required Patterns

### Dependency Injection

All classes with dependencies MUST use constructor injection:

```kotlin
// Good
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val db: MemorandumDatabase
) : TaskRepository

// Bad
class TaskRepositoryImpl {
    private val taskDao = MemorandumDatabase.getInstance().taskDao()
}
```

### Interface Segregation

Repository and client classes MUST have an interface:

```kotlin
// Interface in data/repository/
interface TaskRepository {
    fun observeActiveTasks(): Flow<List<TaskEntity>>
    suspend fun updateStatus(id: String, status: TaskStatus)
}

// Implementation in data/repository/
class TaskRepositoryImpl @Inject constructor(...) : TaskRepository
```

### Coroutine Dispatchers

- Room DAO: Room handles dispatcher internally (no need to specify)
- Network calls: use `Dispatchers.IO` via `withContext`
- Heavy computation (JSON parsing, image processing): use `Dispatchers.Default`
- Never specify dispatcher in UseCase — let the caller decide

### Null Safety

```kotlin
// Good: explicit handling
val task = taskDao.getById(id) ?: return Result.failure(IllegalArgumentException("Task not found: $id"))

// Good: safe call chain
val deadline = entry.deadlineAt?.let { Instant.ofEpochMilli(it) }

// Bad: force unwrap
val task = taskDao.getById(id)!!
```

---

## Testing Requirements

### Unit Tests (Required)

| Layer | What to Test | Framework |
|-------|-------------|-----------|
| DAO | CRUD operations, queries with filters | Room `inMemoryDatabaseBuilder` + JUnit |
| Repository | Transaction correctness, error mapping | Mockk + JUnit |
| UseCase | Business logic, edge cases | Mockk + JUnit |
| SchemaValidator | All validation rules | JUnit |
| TypeConverter | JSON round-trip | JUnit |
| Orchestrator | Flow control, retry, degradation | Mockk + Turbine (for Flow) |

### Instrumented Tests (Required)

| What | Why |
|------|-----|
| Database migrations | Verify data integrity across versions |
| CryptoHelper | Android Keystore only works on device |
| Notification channels | System API behavior |

### Test Naming

```kotlin
@Test
fun `getActiveTasks returns only non-terminal tasks`() { ... }

@Test
fun `saveFromPlan writes all child entities in transaction`() { ... }

@Test
fun `schema validation rejects future clarification when already used`() { ... }
```

Pattern: `` `{method} {expected behavior} {condition}` ``

---

## Code Review Checklist

- [ ] No `!!` usage — use safe alternatives
- [ ] All public functions have clear return types (no `Any`)
- [ ] Repository methods return `Result<T>` or `Flow<T>`
- [ ] Database writes use `@Transaction` when touching multiple tables
- [ ] Sensitive data (API keys) never logged or exposed
- [ ] New DAO queries use indexed columns
- [ ] AI output is validated by `SchemaValidator` before use
- [ ] Error cases are handled with meaningful messages
- [ ] New dependencies added to Version Catalog, not hardcoded
- [ ] Coroutine scope is appropriate (no `GlobalScope`)

---

## Code Style

- Max line length: 120 characters
- Indentation: 4 spaces
- Trailing commas in multi-line parameter lists
- Use `data class` for pure data holders
- Use `object` for stateless singletons (Prompt templates)
- Use `sealed interface` over `sealed class` for error/result types
- Prefer `when` expression over `if-else` chains for 3+ branches
