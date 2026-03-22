# Database Guidelines

> Room database patterns and conventions for Memorandum.

---

## Overview

- ORM: **Room** (AndroidX)
- Database: SQLite (local only, no cloud sync)
- Total tables: 12
- All IDs are UUID strings
- Timestamps are `Long` (epoch milliseconds)
- Enums stored as `TEXT` (enum name)
- JSON arrays stored as `TEXT` via TypeConverter

---

## Query Patterns

### DAO Method Conventions

```kotlin
// One-shot read: suspend fun
suspend fun getById(id: String): TaskEntity?

// Observable read: return Flow
fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>>

// Write: always suspend fun
suspend fun upsert(entity: TaskEntity)

// Batch write: use @Transaction
@Transaction
suspend fun replaceStepsForTask(taskId: String, steps: List<PlanStepEntity>)
```

### Naming Rules for DAO Methods

| Operation | Prefix | Example |
|-----------|--------|---------|
| Single read | `getBy{Field}` | `getById(id)` |
| Observable read | `observe{What}` | `observeActiveTasks()` |
| Insert/Update | `upsert` | `upsert(entity)` |
| Insert only | `insert` / `insertAll` | `insertAll(steps)` |
| Partial update | `update{Field}` | `updateStatus(id, status)` |
| Delete | `delete` / `deleteBy{Field}` | `deleteByTask(taskId)` |
| Count | `count{What}` | `countByStatus(status)` |

### Transaction Rules

- Multi-table writes MUST use `@Transaction`
- Repository is the transaction boundary, not DAO
- Example: `TaskRepository.saveFromPlan()` writes to `tasks`, `plan_steps`, `schedule_blocks`, `prep_items` in one transaction

### Query Performance

- Always use indexed columns in WHERE clauses
- Avoid `SELECT *` in queries that only need a few columns — but acceptable for Room Entity mapping
- Use `LIMIT` for list queries that feed into AI prompts
- Prefer `Flow` for UI-observed data, `suspend` for one-shot reads

---

## Migrations

### Strategy

- `exportSchema = true` in `@Database` annotation
- Schema JSON files stored in `app/schemas/` for version tracking
- Each migration is a separate class: `Migration_X_Y`
- Destructive migration is **FORBIDDEN** in production — user data is local-only and irreplaceable

### Migration Rules

```kotlin
// Good: additive migration
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER DEFAULT NULL")
    }
}

// FORBIDDEN: destructive fallback
// .fallbackToDestructiveMigration()  // NEVER use this
```

---

## Naming Conventions

### Tables

- Lowercase, plural, snake_case: `entries`, `tasks`, `plan_steps`, `schedule_blocks`

### Columns

- Lowercase, snake_case: `created_at`, `entry_id`, `planning_state`
- Foreign key columns: `{referenced_table_singular}_id` → `entry_id`, `task_id`
- Boolean columns: stored as `INTEGER` (0/1), mapped to `Boolean` in Entity
- JSON columns: suffix `_json` → `image_uris_json`, `source_refs_json`

### Indices

- Pattern: `index_{table}_{columns}` → `index_tasks_status_last_progress_at`
- Always index foreign key columns
- Always index columns used in WHERE + ORDER BY combinations

---

## Entity Rules

```kotlin
@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["entry_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("entry_id"), Index("status", "last_progress_at")]
)
data class TaskEntity(
    @PrimaryKey val id: String,                          // UUID
    @ColumnInfo(name = "entry_id") val entryId: String,  // FK naming
    // ...
)
```

### Rules

- `@PrimaryKey` is always `val id: String` (UUID)
- Use `@ColumnInfo(name = "...")` for multi-word column names
- Nullable fields use Kotlin `?` type
- `ForeignKey.CASCADE` for child tables (steps, blocks, preps belong to task)
- Entity classes are pure data — no business methods

---

## TypeConverter

```kotlin
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        try { json.decodeFromString(value) } catch (_: Exception) { emptyList() }
}
```

- One `Converters` class for the entire database
- Use `kotlinx.serialization` for JSON conversion
- Always handle malformed JSON gracefully (return default)

---

## Common Mistakes

- **Forgetting index on FK column** → causes full table scan on JOIN
- **Using `REPLACE` strategy when `UPDATE` is intended** → REPLACE deletes then inserts, triggering CASCADE deletes on child tables
- **Returning `Flow` from a write operation** → write operations should be `suspend`, not `Flow`
- **Running DB operations on main thread** → all DAO methods must be `suspend` or return `Flow`
- **Not testing migrations** → use `MigrationTestHelper` in instrumented tests
