# Logging Guidelines

> Logging conventions for Memorandum.

---

## Overview

- Library: **Android `Log`** (standard `android.util.Log`)
- No external logging framework in MVP
- Tag convention: class name
- Sensitive data must never be logged

---

## Log Levels

| Level | When to Use | Example |
|-------|-------------|---------|
| `Log.v` | Verbose trace, only during active debugging | Prompt text being sent to LLM |
| `Log.d` | Development-useful info, disabled in release | DAO query results, state transitions |
| `Log.i` | Key business events worth tracking | "Planning started for entry X", "Heartbeat triggered" |
| `Log.w` | Recoverable issues, degraded behavior | "MCP call failed, using fallback plan", "Image too large, skipping" |
| `Log.e` | Errors that need attention | "LLM auth failed", "Database migration error", "Schema validation failed" |

---

## Tag Convention

```kotlin
companion object {
    private const val TAG = "PlanningOrchestrator"
}

Log.i(TAG, "Planning started for entry: $entryId")
```

- Tag = simple class name (no package prefix)
- Keep tags under 23 characters (Android limit on older versions)

---

## What to Log

### Must Log (INFO level)

- Heartbeat trigger and result (notified / skipped / failed)
- Planning flow start, clarification, completion, failure
- Memory update trigger and result
- MCP call initiation and result
- Task status changes
- Notification sent / blocked by protection rules
- App lifecycle events (first launch, onboarding complete)

### Should Log (DEBUG level)

- DAO operations with entity IDs
- Prompt assembly details (which memories injected, token estimate)
- Schema validation results
- Retry attempts
- Cache hits/misses (MCP cache)

### Must Log (ERROR level)

- LLM API failures with HTTP status
- JSON parse failures with raw response snippet (first 200 chars)
- Database constraint violations
- Encryption/decryption failures
- Permission denials

---

## What NOT to Log

- **API keys** — never, not even partially
- **Full prompt text** in INFO/WARN/ERROR — only in VERBOSE
- **User input text** in full — truncate to first 50 chars in INFO
- **Image data** — only log URI and size, never base64 content
- **Encrypted values** — never log cipher text
- **Full AI response** in INFO — only in DEBUG, truncated to 500 chars

---

## Structured Context

Always include relevant IDs for traceability:

```kotlin
// Good
Log.i(TAG, "Planning completed: entryId=$entryId, taskId=$taskId, steps=${steps.size}")
Log.w(TAG, "MCP failed: server=${server.name}, error=${e.message}")
Log.e(TAG, "Schema validation failed: entryId=$entryId, errors=$errors")

// Bad
Log.i(TAG, "Planning done")
Log.e(TAG, "Error occurred")
```

---

## Release Build

- `Log.v` and `Log.d` should be stripped or guarded in release builds
- Use ProGuard/R8 rules to strip verbose/debug logs:

```proguard
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
```

---

## Common Mistakes

- **Logging full AI response at INFO level** → floods logcat, may contain user data
- **Forgetting entity ID in error logs** → impossible to trace which operation failed
- **Using string concatenation instead of template** → unnecessary object creation when log level is disabled
- **Logging in tight loops (DAO observers)** → use DEBUG level and guard with `if (BuildConfig.DEBUG)`
