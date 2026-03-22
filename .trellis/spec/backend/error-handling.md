# Error Handling

> Error handling conventions for Memorandum's data, AI, and scheduler layers.

---

## Overview

This is a local-first Android app. There is no backend server returning HTTP errors. Error sources are:
1. **Room database** — schema issues, constraint violations
2. **LLM API calls** — network, auth, timeout, malformed response
3. **MCP calls** — network, auth, tool errors
4. **JSON parsing** — AI output not matching expected schema
5. **System** — permissions, file I/O, WorkManager

---

## Error Types

### Sealed Interface Pattern

Use Kotlin `sealed interface` for domain-specific errors:

```kotlin
// AI layer errors
sealed interface AiError {
    data class NetworkError(val cause: Throwable) : AiError
    data class AuthError(val message: String) : AiError
    data class TimeoutError(val timeoutMs: Long) : AiError
    data class ParseError(val rawResponse: String, val cause: Throwable) : AiError
    data class SchemaValidationError(val errors: List<String>) : AiError
    data class NoConfigError(val message: String) : AiError
}

// MCP layer errors
sealed interface McpError {
    data object NetworkDisabled : McpError
    data object NoServersConfigured : McpError
    data class ConnectionFailed(val server: String, val cause: String) : McpError
    data class AuthFailed(val server: String) : McpError
    data class ToolNotFound(val toolName: String) : McpError
    data class ToolCallFailed(val toolName: String, val error: String) : McpError
    data class Timeout(val server: String) : McpError
}
```

### Result Pattern

Use Kotlin `Result<T>` for operations that can fail:

```kotlin
// Repository / UseCase return types
suspend fun startPlanning(entryId: String): Result<PlanningResult>
suspend fun executeHeartbeat(): Result<HeartbeatResult>
suspend fun callTool(server: McpServerEntity, tool: String, args: Map<String, Any>): Result<McpToolResult>
```

For domain-specific results with multiple outcomes, use sealed interfaces:

```kotlin
sealed interface PlanningResult {
    data class NeedsClarification(val question: String, val reason: String) : PlanningResult
    data class Success(val taskId: String) : PlanningResult
    data class Failed(val error: String) : PlanningResult
}
```

---

## Error Handling Patterns

### Layer-by-Layer

#### Data Layer (Repository)
- Catch database exceptions, wrap in `Result.failure()`
- Log the error with context (which entity, which operation)
- Never throw from Repository — always return `Result`

```kotlin
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val db: MemorandumDatabase
) : TaskRepository {
    override suspend fun saveFromPlan(
        task: TaskEntity,
        steps: List<PlanStepEntity>,
        blocks: List<ScheduleBlockEntity>,
        preps: List<PrepItemEntity>
    ): Result<Unit> = runCatching {
        db.withTransaction {
            taskDao.upsert(task)
            planStepDao.insertAll(steps)
            scheduleBlockDao.insertAll(blocks)
            prepItemDao.insertAll(preps)
        }
    }
}
```

#### AI Layer (Orchestrator)
- Catch network/parse errors from LLM client
- Validate JSON schema before using AI output
- On schema validation failure: return `Failed`, do NOT retry
- On network failure: retry up to 2 times with exponential backoff

```kotlin
class PlanningOrchestrator {
    suspend fun startPlanning(entryId: String): PlanningResult {
        val llmResult = retryWithBackoff(maxRetries = 2) {
            llmClient.chat(request)
        }

        return when {
            llmResult.isFailure -> PlanningResult.Failed(
                "AI call failed: ${llmResult.exceptionOrNull()?.message}"
            )
            else -> {
                val parsed = parseAndValidate(llmResult.getOrThrow().content)
                if (parsed == null) PlanningResult.Failed("Invalid AI response")
                else savePlanAndReturn(parsed)
            }
        }
    }
}
```

#### Scheduler Layer
- WorkManager `doWork()` should return `Result.success()` even on non-fatal errors
- Only return `Result.retry()` for transient network issues
- Never return `Result.failure()` for periodic work (stops future executions)

```kotlin
override suspend fun doWork(): Result {
    return try {
        heartbeatOrchestrator.executeHeartbeat()
        Result.success()
    } catch (e: Exception) {
        Log.e(TAG, "Heartbeat failed", e)
        Result.success()  // Don't stop periodic work
    }
}
```

---

## Retry Policy

```kotlin
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 2,
    initialDelayMs: Long = 2000,
    factor: Double = 2.0,
    block: suspend () -> T
): Result<T> {
    var lastException: Throwable? = null
    repeat(maxRetries + 1) { attempt ->
        try {
            return Result.success(block())
        } catch (e: Exception) {
            lastException = e
            if (attempt < maxRetries) {
                delay((initialDelayMs * factor.pow(attempt)).toLong())
            }
        }
    }
    return Result.failure(lastException!!)
}
```

### What to Retry

| Error Type | Retry? | Max Attempts |
|------------|--------|--------------|
| Network timeout | Yes | 2 |
| HTTP 5xx | Yes | 2 |
| JSON parse error | Yes | 1 (model may output differently) |
| HTTP 401/403 (auth) | No | — |
| Schema validation | No | — |
| Insufficient balance | No | — |

---

## Degradation Strategy

| Failure | Degradation |
|---------|-------------|
| LLM call fails | Set `planning_state = FAILED`, user can retry manually |
| MCP call fails | AI gives plan without external info (fallback plan) |
| Image processing fails | Degrade to text-only input |
| Heartbeat LLM fails | Log error, skip this heartbeat cycle |
| Memory update fails | Log error, skip this update cycle |

---

## Forbidden Patterns

- **NEVER** throw uncaught exceptions from Repository or UseCase
- **NEVER** use `Result.failure()` in WorkManager periodic work
- **NEVER** retry auth errors (401/403) — prompt user to check config
- **NEVER** swallow exceptions silently — always log with context
- **NEVER** show raw exception messages to user — map to user-friendly strings
- **NEVER** use `try-catch(Exception)` around entire functions — catch specific exceptions
