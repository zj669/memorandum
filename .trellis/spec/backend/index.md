# Backend Development Guidelines

> Data layer, AI orchestration, and scheduler conventions for Memorandum.

---

## Overview

This project is an Android local-first app with no backend server. "Backend" refers to **Room database, Repository, UseCase, AI orchestration, MCP integration, and WorkManager scheduling**.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | data/domain/ai/scheduler layout and naming | Done |
| [Database Guidelines](./database-guidelines.md) | Room patterns, DAO, migrations, naming | Done |
| [Error Handling](./error-handling.md) | Result pattern, retry, degradation strategy | Done |
| [Quality Guidelines](./quality-guidelines.md) | Forbidden patterns, testing, code review | Done |
| [Logging Guidelines](./logging-guidelines.md) | Log levels, what to log, sensitive data | Done |

---

## Pre-Development Checklist

Before writing any data/AI/scheduler code, read:

1. **Always**: [Directory Structure](./directory-structure.md) — know where files go, layer dependencies
2. **If touching DB**: [Database Guidelines](./database-guidelines.md) — Entity, DAO, migration rules
3. **If handling errors**: [Error Handling](./error-handling.md) — Result pattern, retry policy
4. **If adding logs**: [Logging Guidelines](./logging-guidelines.md) — levels, sensitive data rules
5. **Before PR**: [Quality Guidelines](./quality-guidelines.md) — review checklist

---

## Quick Reference

### Layer Dependencies

```
ui/ → domain/ → data/
              → ai/
scheduler/ → domain/ → data/
                     → ai/
```

### Key Rules

- Repository returns `Result<T>` or `Flow<T>`, never throws
- All DAO methods are `suspend` or return `Flow`
- Multi-table writes use `@Transaction`
- AI output validated by `SchemaValidator` before use
- WorkManager returns `Result.success()` even on non-fatal errors
- API keys encrypted via Android Keystore
- Never log sensitive data (API keys, full user input, image data)
