# 模块 07 - 长期记忆

## 1. 目标
实现 AI 驱动的长期记忆系统，从用户行为证据中提炼稳定模式、偏好和长期目标，持续更新用户画像，使规划越来越贴合个人习惯。

## 2. 架构

```
domain/usecase/memory/
├── TriggerMemoryUpdateUseCase.kt    # 判断是否触发记忆更新
├── CollectEvidenceUseCase.kt        # 收集行为证据
├── ApplyMemoryOutputUseCase.kt      # 应用 AI 输出到数据库
└── AggregateProfileUseCase.kt       # 聚合用户画像

data/local/room/
├── MemoryEntity.kt                  # (已在模块02定义)
├── UserProfileEntity.kt             # (已在模块02定义)
├── TaskEventEntity.kt               # (已在模块02定义)
├── MemoryDao.kt
├── UserProfileDao.kt
└── TaskEventDao.kt
```

## 3. 记忆触发机制

### 3.1 触发时机
```kotlin
class TriggerMemoryUpdateUseCase @Inject constructor(
    private val taskEventDao: TaskEventDao,
    private val memoryOrchestrator: MemoryOrchestrator,
    private val appPreferencesDataStore: AppPreferencesDataStore
) {
    companion object {
        const val MIN_EVENTS_THRESHOLD = 5        // 累积至少 5 个事件
        const val MIN_INTERVAL_HOURS = 6          // 距上次更新至少 6 小时
    }

    /**
     * 触发条件（满足任一即可）：
     * 1. 任务完成（DONE）
     * 2. 计划被接受（ACCEPTED_PLAN）
     * 3. 通知被响应（点击/忽略/延后）
     * 4. 累积事件数 >= MIN_EVENTS_THRESHOLD 且距上次更新 >= MIN_INTERVAL_HOURS
     */
    suspend fun checkAndTrigger(triggerEvent: String): MemoryUpdateResult {
        val immediateEvents = setOf("DONE", "ACCEPTED_PLAN")

        if (triggerEvent in immediateEvents) {
            return memoryOrchestrator.updateMemories()
        }

        // 累积触发
        val lastUpdateAt = appPreferencesDataStore.getLastMemoryUpdateAt()
        val hoursSinceUpdate = (System.currentTimeMillis() - lastUpdateAt) / 3600_000L
        if (hoursSinceUpdate < MIN_INTERVAL_HOURS) return MemoryUpdateResult.Skipped

        val recentEvents = taskEventDao.getEventsSince(lastUpdateAt)
        if (recentEvents.size < MIN_EVENTS_THRESHOLD) return MemoryUpdateResult.Skipped

        return memoryOrchestrator.updateMemories()
    }
}
```

### 3.2 事件记录
```kotlin
class RecordTaskEventUseCase @Inject constructor(
    private val taskEventDao: TaskEventDao,
    private val triggerMemoryUpdateUseCase: TriggerMemoryUpdateUseCase
) {
    /**
     * 记录任务事件并检查是否触发记忆更新
     */
    suspend fun record(
        taskId: String,
        eventType: String,
        payload: Map<String, Any>? = null
    ) {
        val event = TaskEventEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            eventType = eventType,
            payloadJson = payload?.let { Json.encodeToString(it) },
            createdAt = System.currentTimeMillis()
        )
        taskEventDao.insert(event)

        // 异步触发记忆更新检查
        triggerMemoryUpdateUseCase.checkAndTrigger(eventType)
    }
}
```

## 4. 证据收集

### 4.1 CollectEvidenceUseCase
```kotlin
class CollectEvidenceUseCase @Inject constructor(
    private val taskEventDao: TaskEventDao,
    private val taskDao: TaskDao,
    private val notificationDao: NotificationDao,
    private val scheduleBlockDao: ScheduleBlockDao
) {
    /**
     * 收集最近的行为证据，组装为 AI 可理解的上下文
     */
    suspend fun collect(sinceTimestamp: Long): EvidenceSummary {
        val events = taskEventDao.getEventsSince(sinceTimestamp)
        val taskIds = events.map { it.taskId }.distinct()
        val tasks = taskIds.mapNotNull { taskDao.getById(it) }

        return EvidenceSummary(
            completedTasks = extractCompletedTasks(events, tasks),
            acceptedPlans = extractAcceptedPlans(events, tasks),
            rejectedPlans = extractRejectedPlans(events, tasks),
            notificationResponses = collectNotificationResponses(sinceTimestamp),
            scheduleAdherence = analyzeScheduleAdherence(tasks, sinceTimestamp),
            statusChanges = extractStatusChanges(events, tasks)
        )
    }

    /**
     * 分析排程遵守情况
     * - 用户是否在建议时间段内推进任务
     * - 哪些时间段完成率高
     * - 哪些时间段经常被跳过
     */
    private suspend fun analyzeScheduleAdherence(
        tasks: List<TaskEntity>,
        since: Long
    ): List<ScheduleAdherenceRecord>
}

data class EvidenceSummary(
    val completedTasks: List<CompletedTaskEvidence>,
    val acceptedPlans: List<PlanAcceptanceEvidence>,
    val rejectedPlans: List<PlanRejectionEvidence>,
    val notificationResponses: List<NotificationResponseEvidence>,
    val scheduleAdherence: List<ScheduleAdherenceRecord>,
    val statusChanges: List<StatusChangeEvidence>
) {
    /**
     * 转为 Prompt 可用的文本摘要
     * 控制在 2000 字符以内
     */
    fun toPromptText(): String
}

data class CompletedTaskEvidence(
    val taskId: String,
    val title: String,
    val type: EntryType,
    val totalDurationDays: Int,          // 从创建到完成的天数
    val stepsCompleted: Int,
    val stepsTotal: Int,
    val completedAt: Long
)

data class NotificationResponseEvidence(
    val notificationType: NotificationType,
    val action: String,                   // CLICKED / DISMISSED / SNOOZED
    val taskTitle: String?,
    val responseDelayMinutes: Long?       // 从通知到响应的时间
)

data class ScheduleAdherenceRecord(
    val blockDate: String,
    val startTime: String,
    val endTime: String,
    val taskTitle: String,
    val wasFollowed: Boolean,             // 是否在该时间段有推进
    val actualProgressAt: Long?           // 实际推进时间
)
```

## 5. 记忆应用

### 5.1 ApplyMemoryOutputUseCase
```kotlin
class ApplyMemoryOutputUseCase @Inject constructor(
    private val memoryDao: MemoryDao,
    private val aggregateProfileUseCase: AggregateProfileUseCase
) {
    /**
     * 将 AI 输出的记忆变更应用到数据库
     */
    suspend fun apply(output: MemoryOutput) {
        val now = System.currentTimeMillis()

        // 1. 新增记忆
        output.newMemories.forEach { newMem ->
            val entity = MemoryEntity(
                id = UUID.randomUUID().toString(),
                type = MemoryType.valueOf(newMem.type),
                subject = newMem.subject,
                content = newMem.content,
                confidence = newMem.confidence.coerceIn(0f, 1f),
                sourceRefsJson = newMem.sourceRefs,
                evidenceCount = newMem.sourceRefs.size,
                updatedAt = now,
                lastUsedAt = null
            )
            memoryDao.upsert(entity)
        }

        // 2. 更新记忆
        output.updates.forEach { update ->
            val existing = memoryDao.getById(update.memoryId) ?: return@forEach
            val updated = existing.copy(
                content = update.content ?: existing.content,
                confidence = (update.confidence ?: existing.confidence).coerceIn(0f, 1f),
                sourceRefsJson = existing.sourceRefsJson + update.newSourceRefs,
                evidenceCount = existing.evidenceCount + update.newSourceRefs.size,
                updatedAt = now
            )
            memoryDao.upsert(updated)
        }

        // 3. 降权记忆
        output.downgrades.forEach { downgrade ->
            memoryDao.updateConfidence(
                id = downgrade.memoryId,
                confidence = downgrade.newConfidence.coerceIn(0f, 1f),
                now = now
            )
        }

        // 4. 清理极低置信度记忆（< 0.1）
        memoryDao.deleteByLowConfidence(threshold = 0.1f)

        // 5. 重新聚合用户画像
        aggregateProfileUseCase.aggregate()
    }
}
```

## 6. 用户画像聚合

### 6.1 AggregateProfileUseCase
```kotlin
class AggregateProfileUseCase @Inject constructor(
    private val memoryDao: MemoryDao,
    private val userProfileDao: UserProfileDao
) {
    /**
     * 从所有高置信度记忆中聚合用户画像
     * 画像是记忆的"快照视图"，用于快速注入 Prompt
     */
    suspend fun aggregate() {
        val memories = memoryDao.getHighConfidence(minConfidence = 0.4f)

        val profile = UserProfileJson(
            availableTimeSlots = extractFromMemories(memories, MemoryType.PREFERENCE, "时间"),
            preferredWorkHours = extractFromMemories(memories, MemoryType.PREFERENCE, "工作时段"),
            taskGranularity = extractFromMemories(memories, MemoryType.PREFERENCE, "粒度"),
            notificationTolerance = extractFromMemories(memories, MemoryType.PREFERENCE, "提醒"),
            procrastinationPatterns = extractFromMemories(memories, MemoryType.PATTERN, "拖延"),
            commonBlockers = extractFromMemories(memories, MemoryType.PATTERN, "阻塞"),
            longTermGoals = extractFromMemories(memories, MemoryType.LONG_TERM_GOAL),
            executionHabits = extractFromMemories(memories, MemoryType.PATTERN, "习惯"),
            intensiveTaskDuration = extractFromMemories(memories, MemoryType.PREFERENCE, "时长"),
            prepPreferences = extractFromMemories(memories, MemoryType.PREP_TEMPLATE)
        )

        val existing = userProfileDao.get()
        val newVersion = (existing?.version ?: 0) + 1

        userProfileDao.upsert(
            UserProfileEntity(
                id = "default",
                profileJson = Json.encodeToString(profile),
                version = newVersion,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun extractFromMemories(
        memories: List<MemoryEntity>,
        type: MemoryType,
        subjectKeyword: String? = null
    ): List<String> {
        return memories
            .filter { it.type == type }
            .filter { subjectKeyword == null || it.subject.contains(subjectKeyword) }
            .sortedByDescending { it.confidence }
            .take(3)
            .map { "${it.subject}: ${it.content} (置信度: ${it.confidence})" }
    }
}

@Serializable
data class UserProfileJson(
    val availableTimeSlots: List<String> = emptyList(),
    val preferredWorkHours: List<String> = emptyList(),
    val taskGranularity: List<String> = emptyList(),
    val notificationTolerance: List<String> = emptyList(),
    val procrastinationPatterns: List<String> = emptyList(),
    val commonBlockers: List<String> = emptyList(),
    val longTermGoals: List<String> = emptyList(),
    val executionHabits: List<String> = emptyList(),
    val intensiveTaskDuration: List<String> = emptyList(),
    val prepPreferences: List<String> = emptyList()
)
```

## 7. 记忆在规划中的使用

### 7.1 记忆注入策略
```kotlin
class MemoryInjector @Inject constructor(
    private val memoryDao: MemoryDao,
    private val userProfileDao: UserProfileDao
) {
    /**
     * 为 Planner Prompt 准备记忆上下文
     * 按优先级选择最相关的记忆
     */
    suspend fun prepareForPlanning(entry: EntryEntity): MemoryContext {
        val profile = userProfileDao.get()
        val allMemories = memoryDao.getHighConfidence(0.4f)

        // 按相关性排序
        val relevant = allMemories
            .sortedByDescending { relevanceScore(it, entry) }
            .take(10)

        // 标记使用时间
        relevant.forEach { memoryDao.markUsed(it.id, System.currentTimeMillis()) }

        return MemoryContext(
            profileSummary = profile?.profileJson ?: "{}",
            relevantMemories = relevant,
            totalTokenEstimate = estimateTokens(profile, relevant)
        )
    }

    /**
     * 计算记忆与当前条目的相关性
     * - 类型匹配加分
     * - 主题关键词匹配加分
     * - 置信度加权
     * - 最近使用时间衰减
     */
    private fun relevanceScore(memory: MemoryEntity, entry: EntryEntity): Float

    /**
     * 估算 token 量，超出预算时裁剪
     */
    private fun estimateTokens(
        profile: UserProfileEntity?,
        memories: List<MemoryEntity>
    ): Int
}

data class MemoryContext(
    val profileSummary: String,
    val relevantMemories: List<MemoryEntity>,
    val totalTokenEstimate: Int
) {
    /**
     * 转为 Prompt 注入文本
     * 控制在 1500 字符以内
     */
    fun toPromptText(): String {
        val sb = StringBuilder()
        sb.appendLine("## 用户画像")
        sb.appendLine(profileSummary)
        sb.appendLine()
        sb.appendLine("## 相关记忆")
        relevantMemories.forEach { mem ->
            sb.appendLine("- [${mem.type}] ${mem.subject}: ${mem.content} (置信度: ${mem.confidence})")
        }
        return sb.toString().take(1500)
    }
}
```

## 8. 记忆生命周期管理

### 8.1 记忆衰减
```kotlin
class MemoryDecayManager @Inject constructor(
    private val memoryDao: MemoryDao
) {
    /**
     * 定期执行（可在心跳中附带执行）
     * - 超过 90 天未使用的记忆，置信度 * 0.9
     * - 超过 180 天未使用的记忆，置信度 * 0.7
     * - 置信度 < 0.1 的记忆自动删除
     */
    suspend fun applyDecay() {
        val now = System.currentTimeMillis()
        val day90 = now - 90L * 24 * 3600_000
        val day180 = now - 180L * 24 * 3600_000

        val allMemories = memoryDao.getAll()
        allMemories.forEach { mem ->
            val lastUsed = mem.lastUsedAt ?: mem.updatedAt
            val newConfidence = when {
                lastUsed < day180 -> mem.confidence * 0.7f
                lastUsed < day90 -> mem.confidence * 0.9f
                else -> mem.confidence
            }
            if (newConfidence != mem.confidence) {
                memoryDao.updateConfidence(mem.id, newConfidence, now)
            }
        }

        memoryDao.deleteByLowConfidence(0.1f)
    }
}
```

### 8.2 记忆去重
```kotlin
class MemoryDeduplicator {
    /**
     * 在新增记忆前检查是否与已有记忆重复
     * - 同类型 + 主题相似度 > 0.8 视为重复
     * - 重复时更新已有记忆而非新增
     */
    fun findDuplicate(
        newMemory: NewMemory,
        existing: List<MemoryEntity>
    ): MemoryEntity? {
        return existing
            .filter { it.type.name == newMemory.type }
            .firstOrNull { subjectSimilarity(it.subject, newMemory.subject) > 0.8f }
    }

    private fun subjectSimilarity(a: String, b: String): Float {
        // 简单的字符重叠率计算
        // 后续可升级为更精确的相似度算法
        val setA = a.toSet()
        val setB = b.toSet()
        val intersection = setA.intersect(setB)
        val union = setA.union(setB)
        return if (union.isEmpty()) 0f else intersection.size.toFloat() / union.size
    }
}
```

## 9. Memory 页面数据支持

### 9.1 MemoryDisplayUseCase
```kotlin
class MemoryDisplayUseCase @Inject constructor(
    private val memoryDao: MemoryDao,
    private val userProfileDao: UserProfileDao
) {
    fun observeMemories(typeFilter: MemoryType?): Flow<List<MemoryEntity>> {
        return if (typeFilter != null) {
            memoryDao.observeByType(typeFilter)
        } else {
            memoryDao.observeAll()
        }
    }

    fun observeProfile(): Flow<UserProfileEntity?> = userProfileDao.observe()

    /**
     * 用户手动删除记忆
     * 删除后重新聚合画像
     */
    suspend fun deleteMemory(memoryId: String) {
        memoryDao.deleteById(memoryId)
        AggregateProfileUseCase(memoryDao, userProfileDao).aggregate()
    }
}
```

## 10. 验收标准
- [ ] 任务完成时自动触发记忆更新
- [ ] 计划被接受时自动触发记忆更新
- [ ] 累积事件达到阈值时触发记忆更新
- [ ] AI 输出的新增/更新/降权操作正确应用
- [ ] 用户画像聚合结果正确反映高置信度记忆
- [ ] 记忆注入 Planner 时按相关性排序
- [ ] 记忆衰减机制正常执行
- [ ] 极低置信度记忆被自动清理
- [ ] 重复记忆被合并而非重复创建
- [ ] Memory 页面可查看和删除记忆
- [ ] 删除记忆后画像自动更新

## 11. 依赖关系
- 前置：模块 01（骨架）、模块 02（数据层）、模块 04（AI 编排 - MemoryOrchestrator）
- 协作：模块 03（Memory 页面展示）、模块 06（通知响应触发记忆更新）
- 被依赖：无（终端模块）
