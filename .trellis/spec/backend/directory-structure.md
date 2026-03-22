# Directory Structure

> Android 项目 data / domain / ai / scheduler 层的目录组织规范。

---

## Overview

本项目是 Android 本地优先应用，无自建后端。"Backend" 在本项目中指 **数据持久化、AI 编排、MCP 调用、后台调度** 等非 UI 层代码。

语言：Kotlin，构建：Gradle Kotlin DSL，DI：Hilt。

---

## Directory Layout

```
app/src/main/java/com/memorandum/
├── di/                           # Hilt Module
│   ├── DatabaseModule.kt
│   ├── NetworkModule.kt
│   ├── DataStoreModule.kt
│   └── AiModule.kt
│
├── data/
│   ├── local/
│   │   ├── room/
│   │   │   ├── MemorandumDatabase.kt
│   │   │   ├── Converters.kt
│   │   │   ├── entity/
│   │   │   ├── dao/
│   │   │   └── enums/
│   │   └── datastore/
│   │       └── AppPreferencesDataStore.kt
│   ├── remote/
│   │   ├── llm/
│   │   └── mcp/
│   └── repository/
│
├── domain/
│   └── usecase/
│       ├── planning/
│       ├── memory/
│       ├── config/
│       └── task/
│
├── ai/
│   ├── prompt/
│   ├── schema/
│   └── orchestrator/
│
├── scheduler/
│
└── util/
```

---

## Module Organization

### Rules for Adding New Code

1. **New Entity** → `data/local/room/entity/`, corresponding DAO → `data/local/room/dao/`
2. **New Repository** → `data/repository/`, interface and impl in separate files
3. **New UseCase** → `domain/usecase/{business-domain}/`, one UseCase per file
4. **New Prompt** → `ai/prompt/`, corresponding output schema → `ai/schema/`
5. **New background task** → `scheduler/`

### Layer Dependency Rules

```
ui/ → domain/ → data/
              → ai/
scheduler/ → domain/ → data/
                     → ai/
```

- `data/` MUST NOT depend on `domain/` or `ui/`
- `domain/` MUST NOT depend on `ui/`
- `ai/` can be called by `domain/` and `scheduler/`
- `scheduler/` accesses data through `domain/` UseCases

---

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Entity | `{Name}Entity.kt` | `TaskEntity.kt` |
| DAO | `{Name}Dao.kt` | `TaskDao.kt` |
| Repository interface | `{Name}Repository.kt` | `TaskRepository.kt` |
| Repository impl | `{Name}RepositoryImpl.kt` | `TaskRepositoryImpl.kt` |
| UseCase | `{Verb}{Noun}UseCase.kt` | `StartPlanningUseCase.kt` |
| Prompt | `{Name}Prompt.kt` | `PlannerPrompt.kt` |
| Schema output | `{Name}Output.kt` | `PlannerOutput.kt` |
| Worker | `{Name}Worker.kt` | `HeartbeatWorker.kt` |
| Hilt Module | `{Name}Module.kt` | `DatabaseModule.kt` |
| Enum | PascalCase, no suffix | `EntryType.kt` |

---

## Forbidden Patterns

- **NEVER** let UI layer access DAO or Room Database directly
- **NEVER** reference ViewModel or Composable from Repository
- **NEVER** use Android Context in `data/` layer (except `CryptoHelper`, `ImageProcessor`)
- **NEVER** let UseCases from different domains call each other directly — share data through Repository
- **NEVER** put business logic in Entity classes — Entities are pure data holders
