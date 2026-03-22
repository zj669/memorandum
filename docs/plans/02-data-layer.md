# 模块 02 - 数据层（Room + DataStore）

## 1. 目标
实现完整的本地数据持久化层，包括 Room 数据库的所有表结构、DAO、TypeConverter，以及 DataStore 的配置存储。为上层提供统一的 Repository 接口。

## 2. Room 数据库

### 2.1 数据库定义
```kotlin
@Database(
    entities = [
        EntryEntity::class,
        TaskEntity::class,
        ScheduleBlockEntity::class,
        PlanStepEntity::class,
        PrepItemEntity::class,
        MemoryEntity::class,
        UserProfileEntity::class,
        TaskEventEntity::class,
        HeartbeatLogEntity::class,
        NotificationEntity::class,
        LlmConfigEntity::class,
        McpServerEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MemorandumDatabase : RoomDatabase()
```

### 2.2 枚举类型定义
所有枚举在 `data/local/room/enums/` 下定义，Room 中以 TEXT 存储：

```kotlin
enum class EntryType { TASK, GOAL, MEMO, REFERENCE, REMINDER }
enum class TaskStatus { INBOX, PLANNED, DOING, BLOCKED, DONE, DROPPED }
enum class PlanningState { NOT_STARTED, ASKING, GENERATING, READY, FAILED }
enum class MemoryType { PREFERENCE, PATTERN, LONG_TERM_GOAL, TASK_CONTEXT, PREP_TEMPLATE }
enum class NotificationType { PLAN_READY, TIME_TO_START, DEADLINE_RISK, PREP_NEEDED, STALE_TASK, HEARTBEAT_CHECK }
enum class NotificationActionType { OPEN_TASK, OPEN_TODAY, SNOOZE, MARK_DONE }
enum class StepStatus { TODO, DOING, DONE, SKIPPED }
enum class PrepStatus { TODO, DONE, SKIPPED }
enum class ScheduleSource { PLANNER, USER }
```

### 2.3 TypeConverter
```kotlin
class Converters {
    // String List <-> JSON (用于 image_uris_json, source_refs_json, tool_whitelist_json)
    @TypeConverter fun fromStringList(value: List<String>): String
    @TypeConverter fun toStringList(value: String): List<String>

    // 枚举类型全部使用 name 存储，Room 自动处理
}
```

## 3. Entity 详细设计

### 3.1 EntryEntity
```kotlin
@Entity(
    tableName = "entries",
    indices = [
        Index("type", "created_at"),
        Index("planning_state", "updated_at"),
        Index("deadline_at")
    ]
)
data class EntryEntity(
    @PrimaryKey val id: String,          // UUID
    val type: EntryType,
    val text: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val priority: Int?,                   // 1-5
    @ColumnInfo(name = "deadline_at") val deadlineAt: Long?,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int?,
    @ColumnInfo(name = "image_uris_json") val imageUrisJson: List<String>,
    @ColumnInfo(name = "planning_state") val planningState: PlanningState,
    @ColumnInfo(name = "clarification_used") val clarificationUsed: Boolean,
    @ColumnInfo(name = "clarification_question") val clarificationQuestion: String?,
    @ColumnInfo(name = "clarification_answer") val clarificationAnswer: String?,
    @ColumnInfo(name = "last_planned_at") val lastPlannedAt: Long?
)
```

### 3.2 TaskEntity
```kotlin
@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["entry_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("status", "last_progress_at"),
        Index("risk_level", "status"),
        Index("goal_id"),
        Index("entry_id")
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    val title: String,
    val status: TaskStatus,
    val summary: String,
    @ColumnInfo(name = "goal_id") val goalId: String?,
    @ColumnInfo(name = "next_action") val nextAction: String?,
    @ColumnInfo(name = "risk_level") val riskLevel: Int,       // 0-3
    @ColumnInfo(name = "plan_version") val planVersion: Int,
    @ColumnInfo(name = "plan_ready") val planReady: Boolean,
    @ColumnInfo(name = "last_heartbeat_at") val lastHeartbeatAt: Long?,
    @ColumnInfo(name = "last_progress_at") val lastProgressAt: Long?,
    @ColumnInfo(name = "notification_cooldown_until") val notificationCooldownUntil: Long?
)
```

### 3.3 ScheduleBlockEntity
```kotlin
@Entity(
    tableName = "schedule_blocks",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("task_id", "block_date"),
        Index("block_date", "start_time")
    ]
)
data class ScheduleBlockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "block_date") val blockDate: String,    // YYYY-MM-DD
    @ColumnInfo(name = "start_time") val startTime: String,    // HH:mm
    @ColumnInfo(name = "end_time") val endTime: String,        // HH:mm
    val reason: String,
    val source: ScheduleSource,
    val accepted: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

### 3.4 PlanStepEntity
```kotlin
@Entity(
    tableName = "plan_steps",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("task_id")]
)
data class PlanStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    val title: String,
    val description: String,
    val status: StepStatus,
    @ColumnInfo(name = "needs_mcp") val needsMcp: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

### 3.5 PrepItemEntity
```kotlin
@Entity(
    tableName = "prep_items",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("task_id")]
)
data class PrepItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    val content: String,
    val status: PrepStatus,
    @ColumnInfo(name = "source_memory_id") val sourceMemoryId: String?
)
```

### 3.6 MemoryEntity
```kotlin
@Entity(
    tableName = "memories",
    indices = [
        Index("type", "confidence"),
        Index("subject")
    ]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val type: MemoryType,
    val subject: String,
    val content: String,
    val confidence: Float,                // 0-1
    @ColumnInfo(name = "source_refs_json") val sourceRefsJson: List<String>,
    @ColumnInfo(name = "evidence_count") val evidenceCount: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long?
)
```

### 3.7 UserProfileEntity
```kotlin
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String,           // 固定 "default"
    @ColumnInfo(name = "profile_json") val profileJson: String,
    val version: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

### 3.8 TaskEventEntity
```kotlin
@Entity(
    tableName = "task_events",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("task_id", "created_at")]
)
data class TaskEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "event_type") val eventType: String,    // CREATED/PLANNED/STARTED/DONE/DROPPED/SNOOZED/ACCEPTED_PLAN/REJECTED_PLAN
    @ColumnInfo(name = "payload_json") val payloadJson: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

### 3.9 HeartbeatLogEntity
```kotlin
@Entity(tableName = "heartbeat_logs")
data class HeartbeatLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "checked_at") val checkedAt: Long,
    @ColumnInfo(name = "should_notify") val shouldNotify: Boolean,
    @ColumnInfo(name = "notification_type") val notificationType: NotificationType?,
    val reason: String,
    @ColumnInfo(name = "task_ref") val taskRef: String?,
    @ColumnInfo(name = "used_mcp") val usedMcp: Boolean,
    @ColumnInfo(name = "mcp_summary") val mcpSummary: String?
)
```

### 3.10 NotificationEntity
```kotlin
@Entity(
    tableName = "notifications",
    indices = [
        Index("task_ref", "created_at"),
        Index("type", "created_at")
    ]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val type: NotificationType,
    @ColumnInfo(name = "action_type") val actionType: NotificationActionType,
    val title: String,
    val body: String,
    @ColumnInfo(name = "task_ref") val taskRef: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "clicked_at") val clickedAt: Long?,
    @ColumnInfo(name = "dismissed_at") val dismissedAt: Long?,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long?
)
```

### 3.11 LlmConfigEntity
```kotlin
@Entity(tableName = "llm_configs")
data class LlmConfigEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "provider_name") val providerName: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "model_name") val modelName: String,
    @ColumnInfo(name = "api_key_encrypted") val apiKeyEncrypted: String,
    @ColumnInfo(name = "supports_image") val supportsImage: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

### 3.12 McpServerEntity
```kotlin
@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "auth_type") val authType: String,      // NONE/BEARER/HEADER
    @ColumnInfo(name = "auth_value_encrypted") val authValueEncrypted: String?,
    val enabled: Boolean,
    @ColumnInfo(name = "tool_whitelist_json") val toolWhitelistJson: List<String>,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

## 4. DAO 设计

### 4.1 EntryDao
```kotlin
@Dao
interface EntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: String): Flow<EntryEntity?>

    @Query("SELECT * FROM entries WHERE type = :type ORDER BY created_at DESC")
    fun observeByType(type: EntryType): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE planning_state IN (:states) ORDER BY updated_at ASC")
    suspend fun getByPlanningStates(states: List<PlanningState>): List<EntryEntity>

    @Query("UPDATE entries SET planning_state = :state, updated_at = :now WHERE id = :id")
    suspend fun updatePlanningState(id: String, state: PlanningState, now: Long)

    @Query("""UPDATE entries SET
        clarification_used = 1,
        clarification_question = :question,
        clarification_answer = :answer,
        updated_at = :now
        WHERE id = :id""")
    suspend fun saveClarification(id: String, question: String, answer: String?, now: Long)

    @Delete
    suspend fun delete(entry: EntryEntity)
}
```

### 4.2 TaskDao
```kotlin
@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY last_progress_at DESC")
    fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status NOT IN ('DONE', 'DROPPED') ORDER BY risk_level DESC, last_progress_at ASC")
    fun observeActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status NOT IN ('DONE', 'DROPPED')")
    suspend fun getActiveTasks(): List<TaskEntity>

    @Query("""SELECT t.* FROM tasks t
        INNER JOIN schedule_blocks sb ON t.id = sb.task_id
        WHERE sb.block_date = :date
        ORDER BY sb.start_time ASC""")
    fun observeTasksForDate(date: String): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET status = :status, last_progress_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: TaskStatus, now: Long)

    @Query("UPDATE tasks SET last_heartbeat_at = :now WHERE id = :id")
    suspend fun updateHeartbeatTime(id: String, now: Long)

    @Query("UPDATE tasks SET notification_cooldown_until = :until WHERE id = :id")
    suspend fun updateCooldown(id: String, until: Long)
}
```

### 4.3 ScheduleBlockDao
```kotlin
@Dao
interface ScheduleBlockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<ScheduleBlockEntity>)

    @Query("SELECT * FROM schedule_blocks WHERE task_id = :taskId ORDER BY block_date, start_time")
    fun observeByTask(taskId: String): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE block_date = :date ORDER BY start_time")
    fun observeByDate(date: String): Flow<List<ScheduleBlockEntity>>

    @Query("DELETE FROM schedule_blocks WHERE task_id = :taskId AND source = 'PLANNER'")
    suspend fun deletePlannerBlocksForTask(taskId: String)
}
```

### 4.4 PlanStepDao
```kotlin
@Dao
interface PlanStepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(steps: List<PlanStepEntity>)

    @Query("SELECT * FROM plan_steps WHERE task_id = :taskId ORDER BY step_index")
    fun observeByTask(taskId: String): Flow<List<PlanStepEntity>>

    @Query("UPDATE plan_steps SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: StepStatus, now: Long)

    @Query("DELETE FROM plan_steps WHERE task_id = :taskId")
    suspend fun deleteByTask(taskId: String)
}
```

### 4.5 PrepItemDao
```kotlin
@Dao
interface PrepItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PrepItemEntity>)

    @Query("SELECT * FROM prep_items WHERE task_id = :taskId")
    fun observeByTask(taskId: String): Flow<List<PrepItemEntity>>

    @Query("UPDATE prep_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: PrepStatus)

    @Query("DELETE FROM prep_items WHERE task_id = :taskId")
    suspend fun deleteByTask(taskId: String)
}
```

### 4.6 MemoryDao
```kotlin
@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE type = :type ORDER BY confidence DESC")
    fun observeByType(type: MemoryType): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE confidence >= :minConfidence ORDER BY updated_at DESC")
    suspend fun getHighConfidence(minConfidence: Float = 0.5f): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY confidence DESC LIMIT :limit")
    suspend fun getTopMemories(limit: Int = 20): List<MemoryEntity>

    @Query("UPDATE memories SET confidence = :confidence, updated_at = :now WHERE id = :id")
    suspend fun updateConfidence(id: String, confidence: Float, now: Long)

    @Query("UPDATE memories SET last_used_at = :now WHERE id = :id")
    suspend fun markUsed(id: String, now: Long)

    @Delete
    suspend fun delete(memory: MemoryEntity)
}
```

### 4.7 UserProfileDao
```kotlin
@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = 'default'")
    suspend fun get(): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE id = 'default'")
    fun observe(): Flow<UserProfileEntity?>
}
```

### 4.8 TaskEventDao
```kotlin
@Dao
interface TaskEventDao {
    @Insert
    suspend fun insert(event: TaskEventEntity)

    @Query("SELECT * FROM task_events WHERE task_id = :taskId ORDER BY created_at DESC")
    fun observeByTask(taskId: String): Flow<List<TaskEventEntity>>

    @Query("SELECT * FROM task_events ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<TaskEventEntity>
}
```

### 4.9 HeartbeatLogDao
```kotlin
@Dao
interface HeartbeatLogDao {
    @Insert
    suspend fun insert(log: HeartbeatLogEntity)

    @Query("SELECT * FROM heartbeat_logs ORDER BY checked_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<HeartbeatLogEntity>

    @Query("SELECT * FROM heartbeat_logs ORDER BY checked_at DESC LIMIT 1")
    fun observeLatest(): Flow<HeartbeatLogEntity?>
}
```

### 4.10 NotificationDao
```kotlin
@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY created_at DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE task_ref = :taskRef ORDER BY created_at DESC")
    fun observeByTask(taskRef: String): Flow<List<NotificationEntity>>

    @Query("""SELECT * FROM notifications
        WHERE task_ref = :taskRef AND type = :type AND created_at > :since""")
    suspend fun getRecentForTaskAndType(taskRef: String, type: NotificationType, since: Long): List<NotificationEntity>

    @Query("UPDATE notifications SET clicked_at = :now WHERE id = :id")
    suspend fun markClicked(id: String, now: Long)

    @Query("UPDATE notifications SET dismissed_at = :now WHERE id = :id")
    suspend fun markDismissed(id: String, now: Long)

    @Query("UPDATE notifications SET snoozed_until = :until WHERE id = :id")
    suspend fun markSnoozed(id: String, until: Long)
}
```

### 4.11 LlmConfigDao
```kotlin
@Dao
interface LlmConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: LlmConfigEntity)

    @Query("SELECT * FROM llm_configs ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<LlmConfigEntity>>

    @Query("SELECT * FROM llm_configs ORDER BY updated_at DESC LIMIT 1")
    suspend fun getDefault(): LlmConfigEntity?

    @Delete
    suspend fun delete(config: LlmConfigEntity)
}
```

### 4.12 McpServerDao
```kotlin
@Dao
interface McpServerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: McpServerEntity)

    @Query("SELECT * FROM mcp_servers ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE enabled = 1")
    suspend fun getEnabled(): List<McpServerEntity>

    @Delete
    suspend fun delete(server: McpServerEntity)
}
```

## 5. DataStore 配置

### 5.1 AppPreferences
用于存储非结构化的应用配置：

```kotlin
data class AppPreferences(
    val heartbeatFrequency: HeartbeatFrequency = HeartbeatFrequency.MEDIUM,
    val quietHoursStart: String = "23:00",       // HH:mm
    val quietHoursEnd: String = "07:00",
    val allowNetworkAccess: Boolean = false,
    val lastHeartbeatAt: Long = 0L,
    val onboardingCompleted: Boolean = false
)

enum class HeartbeatFrequency(val intervalMinutes: Long) {
    LOW(120),
    MEDIUM(60),
    HIGH(30)
}
```

### 5.2 DataStore 实现
```kotlin
class AppPreferencesDataStore(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<AppPreferences>
    suspend fun updateHeartbeatFrequency(freq: HeartbeatFrequency)
    suspend fun updateQuietHours(start: String, end: String)
    suspend fun updateAllowNetwork(allow: Boolean)
    suspend fun updateLastHeartbeat(timestamp: Long)
    suspend fun setOnboardingCompleted()
}
```

## 6. Repository 层

### 6.1 Repository 接口设计原则
- 每个业务域一个 Repository
- 返回 Flow 用于 UI 观察，suspend 用于一次性操作
- Repository 内部处理跨表事务

### 6.2 Repository 列表
```kotlin
interface EntryRepository {
    suspend fun create(entry: EntryEntity): String
    fun observeById(id: String): Flow<EntryEntity?>
    suspend fun updatePlanningState(id: String, state: PlanningState)
    suspend fun saveClarification(id: String, question: String, answer: String?)
}

interface TaskRepository {
    suspend fun saveFromPlan(task: TaskEntity, steps: List<PlanStepEntity>, blocks: List<ScheduleBlockEntity>, preps: List<PrepItemEntity>)
    fun observeActiveTasks(): Flow<List<TaskEntity>>
    fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>>
    fun observeTasksForDate(date: String): Flow<List<TaskEntity>>
    suspend fun updateStatus(id: String, status: TaskStatus)
    suspend fun recordEvent(taskId: String, eventType: String, payload: String? = null)
}

interface MemoryRepository {
    suspend fun upsert(memory: MemoryEntity)
    suspend fun getForPlanning(): List<MemoryEntity>
    fun observeByType(type: MemoryType): Flow<List<MemoryEntity>>
    suspend fun downgrade(id: String, newConfidence: Float)
}

interface NotificationRepository {
    suspend fun save(notification: NotificationEntity)
    fun observeAll(): Flow<List<NotificationEntity>>
    suspend fun isDuplicate(taskRef: String, type: NotificationType, windowMs: Long): Boolean
    suspend fun markClicked(id: String)
    suspend fun markSnoozed(id: String, until: Long)
}

interface ConfigRepository {
    fun observeLlmConfigs(): Flow<List<LlmConfigEntity>>
    suspend fun getDefaultLlm(): LlmConfigEntity?
    suspend fun saveLlm(config: LlmConfigEntity)
    suspend fun deleteLlm(config: LlmConfigEntity)
    fun observeMcpServers(): Flow<List<McpServerEntity>>
    suspend fun getEnabledMcpServers(): List<McpServerEntity>
    suspend fun saveMcp(server: McpServerEntity)
    suspend fun deleteMcp(server: McpServerEntity)
}
```

## 7. 加密策略
- API Key 使用 Android Keystore + AES-GCM 加密后存储
- 封装为 `CryptoHelper`，提供 `encrypt(plainText): String` 和 `decrypt(cipherText): String`
- 加密密钥绑定设备，不可导出

## 8. 验收标准
- [ ] 所有 12 张表可正常创建
- [ ] 所有 DAO 的 CRUD 操作通过单元测试
- [ ] TypeConverter 正确处理 List<String> <-> JSON
- [ ] DataStore 可正常读写 AppPreferences
- [ ] Repository 层事务操作正确（如 saveFromPlan 涉及多表写入）
- [ ] CryptoHelper 加解密正确
- [ ] 数据库 Migration 策略已建立（exportSchema = true）

## 9. 依赖关系
- 前置：模块 01（项目骨架）
- 被依赖：模块 03（UI）、模块 04（AI 编排）、模块 05（MCP）、模块 06（心跳通知）、模块 07（长期记忆）
