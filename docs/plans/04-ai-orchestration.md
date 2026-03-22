# 模块 04 - AI 编排层

## 1. 目标
实现 AI 调用的完整编排逻辑，包括 4 套 Prompt 的管理、LLM API 调用、JSON 响应解析与校验、以及规划流程的状态机控制。

## 2. 架构概览

```
ai/
├── prompt/
│   ├── PromptBuilder.kt          # Prompt 组装器
│   ├── PlannerPrompt.kt          # 规划 Prompt 模板
│   ├── ClarifierPrompt.kt        # 补充提问 Prompt 模板
│   ├── HeartbeatPrompt.kt        # 心跳 Prompt 模板
│   └── MemoryPrompt.kt           # 记忆 Prompt 模板
├── schema/
│   ├── PlannerOutput.kt          # 规划输出数据类
│   ├── ClarifierOutput.kt        # 补充提问输出数据类
│   ├── HeartbeatOutput.kt        # 心跳输出数据类
│   ├── MemoryOutput.kt           # 记忆输出数据类
│   └── SchemaValidator.kt        # JSON Schema 校验
├── orchestrator/
│   ├── PlanningOrchestrator.kt   # 规划流程编排
│   ├── HeartbeatOrchestrator.kt  # 心跳流程编排
│   └── MemoryOrchestrator.kt     # 记忆更新编排
└── client/
    ├── LlmClient.kt              # LLM API 客户端接口
    ├── OpenAiCompatibleClient.kt # OpenAI 兼容 API 实现
    └── LlmResponse.kt            # 统一响应封装
```

## 3. LLM 客户端

### 3.1 接口定义
```kotlin
interface LlmClient {
    suspend fun chat(request: LlmRequest): Result<LlmResponse>
    suspend fun testConnection(): Result<Boolean>
}

data class LlmRequest(
    val systemPrompt: String,
    val userMessage: String,
    val images: List<ImageInput> = emptyList(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val responseFormat: ResponseFormat = ResponseFormat.JSON
)

data class ImageInput(
    val uri: String,
    val base64Data: String,
    val mimeType: String
)

data class LlmResponse(
    val content: String,
    val usage: TokenUsage?,
    val finishReason: String?
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

enum class ResponseFormat { JSON, TEXT }
```

### 3.2 OpenAI 兼容实现
```kotlin
class OpenAiCompatibleClient(
    private val config: LlmConfigEntity,
    private val okHttpClient: OkHttpClient,
    private val cryptoHelper: CryptoHelper
) : LlmClient {

    // POST {baseUrl}/v1/chat/completions
    // 支持 text + image 多模态输入
    // 图片通过 base64 编码传入 content 数组
    // 强制 response_format: { type: "json_object" }
    // 超时：连接 30s，读取 120s（AI 生成较慢）
}
```

### 3.3 图片处理
```kotlin
class ImageProcessor(private val context: Context) {
    // 从 URI 读取图片
    // 压缩到合理尺寸（长边不超过 1024px）
    // 转为 base64
    // 检测 MIME 类型
    suspend fun processForLlm(uris: List<String>): List<ImageInput>

    // 若模型不支持图片，返回空列表并在 prompt 中说明
    suspend fun processOrSkip(uris: List<String>, supportsImage: Boolean): List<ImageInput>
}
```

## 4. Prompt 模板设计

### 4.1 PromptBuilder
```kotlin
class PromptBuilder {
    // 组装 system prompt 的通用部分
    fun buildSystemBase(): String {
        // 角色定义、输出格式要求、语言要求
    }

    // 注入用户画像上下文
    fun appendUserProfile(profile: UserProfileEntity?): String

    // 注入相关记忆
    fun appendMemories(memories: List<MemoryEntity>): String

    // 注入当前时间上下文
    fun appendTimeContext(): String
}
```

### 4.2 PlannerPrompt
```kotlin
object PlannerPrompt {
    fun build(
        entry: EntryEntity,
        userProfile: UserProfileEntity?,
        memories: List<MemoryEntity>,
        similarTasks: List<TaskEntity>,
        clarificationAnswer: String?,
        mcpResults: String?,
        clarificationUsed: Boolean
    ): LlmRequest

    // System Prompt 核心指令：
    // - 你是一个个人规划助手
    // - 基于用户输入和上下文进行任务拆解、排程
    // - 输出严格 JSON 格式
    // - 如果 clarificationUsed=true，不得再次提问
    // - schedule_blocks 的日期必须 >= 今天
    // - 时间格式 HH:mm，日期格式 YYYY-MM-DD
    // - risks 数组可以为空但必须存在
    // - steps 至少 1 个
}
```

### 4.3 ClarifierPrompt
```kotlin
object ClarifierPrompt {
    fun build(
        entry: EntryEntity,
        userProfile: UserProfileEntity?,
        memories: List<MemoryEntity>
    ): LlmRequest

    // 指令：
    // - 判断是否需要补充提问
    // - 只允许问一个问题
    // - 问题必须直接影响排程/拆解质量
    // - 如果信息足够，ask=false
}
```

### 4.4 HeartbeatPrompt
```kotlin
object HeartbeatPrompt {
    fun build(
        activeTasks: List<TaskEntity>,
        recentNotifications: List<NotificationEntity>,
        userProfile: UserProfileEntity?,
        memories: List<MemoryEntity>,
        recentHeartbeats: List<HeartbeatLogEntity>
    ): LlmRequest

    // 指令：
    // - 评估当前任务状态，决定是否通知
    // - 避免重复通知（参考 recentNotifications）
    // - 通知理由必须具体
    // - cooldown_hours 建议值
    // - should_notify=false 时 notification 必须为 null
}
```

### 4.5 MemoryPrompt
```kotlin
object MemoryPrompt {
    fun build(
        recentEvents: List<TaskEventEntity>,
        existingMemories: List<MemoryEntity>,
        userProfile: UserProfileEntity?
    ): LlmRequest

    // 指令：
    // - 从行为证据中提炼稳定模式
    // - 新增记忆需要足够证据
    // - 可以更新已有记忆的置信度
    // - 可以降权不再成立的记忆
    // - source_refs 至少 1 个
}
```

## 5. 输出 Schema 定义

### 5.1 PlannerOutput
```kotlin
@Serializable
data class PlannerOutput(
    @SerialName("needs_clarification") val needsClarification: Boolean,
    @SerialName("clarification_question") val clarificationQuestion: String? = null,
    @SerialName("clarification_reason") val clarificationReason: String? = null,
    @SerialName("should_use_mcp") val shouldUseMcp: Boolean = false,
    @SerialName("mcp_queries") val mcpQueries: List<String> = emptyList(),
    @SerialName("task_title") val taskTitle: String? = null,
    val summary: String? = null,
    val steps: List<PlanStep> = emptyList(),
    @SerialName("schedule_blocks") val scheduleBlocks: List<ScheduleBlock> = emptyList(),
    @SerialName("prep_items") val prepItems: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    @SerialName("notification_candidates") val notificationCandidates: List<NotificationCandidate> = emptyList()
)

@Serializable
data class PlanStep(
    val index: Int,
    val title: String,
    val description: String,
    @SerialName("needs_mcp") val needsMcp: Boolean = false
)

@Serializable
data class ScheduleBlock(
    val date: String,
    val start: String,
    val end: String,
    val reason: String
)

@Serializable
data class NotificationCandidate(
    val type: String,
    val title: String,
    val body: String,
    @SerialName("action_type") val actionType: String
)
```

### 5.2 ClarifierOutput
```kotlin
@Serializable
data class ClarifierOutput(
    val ask: Boolean,
    val question: String? = null,
    val reason: String? = null
)
```

### 5.3 HeartbeatOutput
```kotlin
@Serializable
data class HeartbeatOutput(
    @SerialName("should_use_mcp") val shouldUseMcp: Boolean = false,
    @SerialName("mcp_queries") val mcpQueries: List<String> = emptyList(),
    @SerialName("should_notify") val shouldNotify: Boolean,
    val notification: HeartbeatNotification? = null,
    val reason: String,
    @SerialName("task_ref") val taskRef: String? = null,
    @SerialName("cooldown_hours") val cooldownHours: Int = 4
)

@Serializable
data class HeartbeatNotification(
    val type: String,
    @SerialName("action_type") val actionType: String,
    val title: String,
    val body: String
)
```

### 5.4 MemoryOutput
```kotlin
@Serializable
data class MemoryOutput(
    @SerialName("new_memories") val newMemories: List<NewMemory> = emptyList(),
    val updates: List<MemoryUpdate> = emptyList(),
    val downgrades: List<MemoryDowngrade> = emptyList()
)

@Serializable
data class NewMemory(
    val type: String,
    val subject: String,
    val content: String,
    val confidence: Float,
    @SerialName("source_refs") val sourceRefs: List<String>
)

@Serializable
data class MemoryUpdate(
    @SerialName("memory_id") val memoryId: String,
    val content: String? = null,
    val confidence: Float? = null,
    @SerialName("new_source_refs") val newSourceRefs: List<String> = emptyList()
)

@Serializable
data class MemoryDowngrade(
    @SerialName("memory_id") val memoryId: String,
    @SerialName("new_confidence") val newConfidence: Float,
    val reason: String
)
```

## 6. Schema 校验

### 6.1 SchemaValidator
```kotlin
class SchemaValidator {
    fun validatePlannerOutput(output: PlannerOutput): ValidationResult {
        // 1. needs_clarification=true 时不能有最终计划
        // 2. needs_clarification=false 时必须有 task_title 和 summary
        // 3. steps 至少 1 个（非 clarification 时）
        // 4. schedule_blocks 日期 >= 今天
        // 5. 时间格式校验 HH:mm
        // 6. mcp_queries 最多 3 条
    }

    fun validateHeartbeatOutput(output: HeartbeatOutput): ValidationResult {
        // 1. should_notify=false 时 notification 必须为 null
        // 2. should_notify=true 时 notification 不能为 null
        // 3. cooldown_hours >= 1
        // 4. notification.type 必须是合法枚举值
    }

    fun validateMemoryOutput(output: MemoryOutput): ValidationResult {
        // 1. new_memories 的 confidence 在 0-1 之间
        // 2. source_refs 至少 1 个
        // 3. updates 引用的 memory_id 必须存在
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)
```

## 7. 编排器

### 7.1 PlanningOrchestrator
```kotlin
class PlanningOrchestrator @Inject constructor(
    private val llmClient: LlmClient,
    private val entryRepository: EntryRepository,
    private val taskRepository: TaskRepository,
    private val memoryRepository: MemoryRepository,
    private val userProfileDao: UserProfileDao,
    private val mcpClient: McpClient,
    private val imageProcessor: ImageProcessor,
    private val schemaValidator: SchemaValidator
) {
    /**
     * 完整规划流程：
     * 1. 读取条目 + 上下文
     * 2. 判断是否需要补充提问（ClarifierPrompt）
     * 3. 如需提问 -> 更新状态为 ASKING，等待用户回答
     * 4. 用户回答/跳过后 -> 执行 PlannerPrompt
     * 5. 如需 MCP -> 调用 MCP -> 带结果重新执行 PlannerPrompt
     * 6. 校验输出 -> 写入数据库
     * 7. 更新状态为 READY 或 FAILED
     */
    suspend fun startPlanning(entryId: String): PlanningResult

    /**
     * 用户回答补充提问后继续规划
     */
    suspend fun continueAfterClarification(entryId: String, answer: String?): PlanningResult

    /**
     * 重新规划（从 Task Detail 触发）
     */
    suspend fun replan(taskId: String): PlanningResult

    // 内部方法
    private suspend fun executePlanner(entry: EntryEntity, context: PlanningContext): PlannerOutput
    private suspend fun executeClarifier(entry: EntryEntity, context: PlanningContext): ClarifierOutput
    private suspend fun savePlanToDatabase(entryId: String, output: PlannerOutput)
    private suspend fun buildPlanningContext(entry: EntryEntity): PlanningContext
    private suspend fun findSimilarTasks(entry: EntryEntity): List<TaskEntity>
}

data class PlanningContext(
    val userProfile: UserProfileEntity?,
    val memories: List<MemoryEntity>,
    val similarTasks: List<TaskEntity>,
    val images: List<ImageInput>
)

sealed interface PlanningResult {
    data class NeedsClarification(val question: String, val reason: String) : PlanningResult
    data class Success(val taskId: String) : PlanningResult
    data class Failed(val error: String) : PlanningResult
}
```

### 7.2 HeartbeatOrchestrator
```kotlin
class HeartbeatOrchestrator @Inject constructor(
    private val llmClient: LlmClient,
    private val taskRepository: TaskRepository,
    private val notificationRepository: NotificationRepository,
    private val memoryRepository: MemoryRepository,
    private val userProfileDao: UserProfileDao,
    private val heartbeatLogDao: HeartbeatLogDao,
    private val mcpClient: McpClient,
    private val notificationHelper: NotificationHelper,
    private val schemaValidator: SchemaValidator,
    private val appPreferencesDataStore: AppPreferencesDataStore
) {
    /**
     * 执行一次心跳：
     * 1. 汇总活跃任务、通知历史、用户画像
     * 2. 调用 HeartbeatPrompt
     * 3. 如需 MCP -> 调用 -> 重新执行
     * 4. 校验输出
     * 5. 客户端去重 + 静默时段 + 冷却判断
     * 6. 符合条件则发送通知
     * 7. 写入心跳日志
     */
    suspend fun executeHeartbeat(): HeartbeatResult

    private suspend fun isInQuietHours(): Boolean
    private suspend fun isTaskInCooldown(taskRef: String): Boolean
    private suspend fun isDuplicateNotification(taskRef: String, type: NotificationType): Boolean
}

sealed interface HeartbeatResult {
    data class Notified(val notificationId: String) : HeartbeatResult
    data class Skipped(val reason: String) : HeartbeatResult
    data class Failed(val error: String) : HeartbeatResult
}
```

### 7.3 MemoryOrchestrator
```kotlin
class MemoryOrchestrator @Inject constructor(
    private val llmClient: LlmClient,
    private val memoryRepository: MemoryRepository,
    private val userProfileDao: UserProfileDao,
    private val taskEventDao: TaskEventDao,
    private val schemaValidator: SchemaValidator
) {
    /**
     * 触发记忆更新：
     * 1. 收集最近的行为证据
     * 2. 读取现有记忆
     * 3. 调用 MemoryPrompt
     * 4. 校验输出
     * 5. 执行新增/更新/降权
     * 6. 更新用户画像聚合
     */
    suspend fun updateMemories(): MemoryUpdateResult

    /**
     * 触发时机：
     * - 任务完成时
     * - 计划被接受时
     * - 通知被响应时
     * - 累积一定数量事件后
     */
    private suspend fun shouldTrigger(): Boolean
    private suspend fun aggregateUserProfile(memories: List<MemoryEntity>): String
}

sealed interface MemoryUpdateResult {
    data class Updated(val added: Int, val updated: Int, val downgraded: Int) : MemoryUpdateResult
    data object Skipped : MemoryUpdateResult
    data class Failed(val error: String) : MemoryUpdateResult
}
```

## 8. 错误处理与重试

### 8.1 策略
```kotlin
class LlmRetryPolicy {
    // 网络错误：最多重试 2 次，指数退避（2s, 4s）
    // JSON 解析失败：重试 1 次（可能是模型输出不规范）
    // Schema 校验失败：不重试，返回 Failed
    // 超时：不重试，返回 Failed
    // API Key 无效：不重试，提示用户检查配置
    // 余额不足：不重试，提示用户
}
```

### 8.2 降级策略
- LLM 调用失败时，规划状态设为 FAILED，用户可手动重试
- MCP 调用失败时，AI 仍需给出不依赖联网的保底计划
- 图片处理失败时，降级为纯文本输入

## 9. Token 管理
- 记录每次调用的 token 用量
- 用户画像和记忆注入时，按 token 预算裁剪
- 优先注入高置信度记忆
- 相似任务摘要限制在 3 条以内

## 10. 验收标准
- [ ] LLM 客户端可成功调用 OpenAI 兼容 API
- [ ] 4 套 Prompt 可正确组装并获得合法 JSON 响应
- [ ] Schema 校验覆盖所有约束条件
- [ ] PlanningOrchestrator 完整流程可走通（含补充提问）
- [ ] MCP 失败时可降级为保底计划
- [ ] 图片可正确编码并传入多模态 API
- [ ] 重试策略正确执行
- [ ] Token 用量有记录

## 11. 依赖关系
- 前置：模块 01（骨架）、模块 02（数据层）
- 协作：模块 03（UI 触发规划、展示结果）、模块 05（MCP 调用）
- 被依赖：模块 06（心跳编排）、模块 07（记忆编排）
