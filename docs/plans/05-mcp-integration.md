# 模块 05 - MCP 工具调用

## 1. 目标
实现远程 HTTP MCP 客户端，支持 AI 在规划和心跳流程中按需调用外部搜索能力。包含请求组装、结果裁剪、失败回退和隐私保护。

## 2. 架构

```
data/remote/mcp/
├── McpClient.kt              # MCP 客户端接口
├── HttpMcpClient.kt          # 远程 HTTP MCP 实现
├── McpRequest.kt             # 请求模型
├── McpResponse.kt            # 响应模型
├── McpResultTrimmer.kt       # 结果裁剪与规范化
├── McpCache.kt               # 短期缓存（避免重复请求）
└── McpPrivacyFilter.kt       # 隐私过滤（发送摘要而非完整上下文）
```

## 3. MCP 协议适配

### 3.1 客户端接口
```kotlin
interface McpClient {
    /**
     * 发现可用工具列表
     */
    suspend fun listTools(server: McpServerEntity): Result<List<McpTool>>

    /**
     * 调用单个工具
     */
    suspend fun callTool(
        server: McpServerEntity,
        toolName: String,
        arguments: Map<String, Any>
    ): Result<McpToolResult>

    /**
     * 测试连接
     */
    suspend fun testConnection(server: McpServerEntity): Result<Boolean>
}

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

data class McpToolResult(
    val content: String,
    val isError: Boolean = false
)
```

### 3.2 HTTP MCP 实现
```kotlin
class HttpMcpClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cryptoHelper: CryptoHelper,
    private val json: Json
) : McpClient {

    // MCP over HTTP 使用 JSON-RPC 2.0 协议
    // POST {baseUrl}
    // Content-Type: application/json

    // tools/list 请求
    // {
    //   "jsonrpc": "2.0",
    //   "id": 1,
    //   "method": "tools/list"
    // }

    // tools/call 请求
    // {
    //   "jsonrpc": "2.0",
    //   "id": 2,
    //   "method": "tools/call",
    //   "params": {
    //     "name": "search",
    //     "arguments": { "query": "..." }
    //   }
    // }

    // 认证方式：
    // NONE: 不加认证头
    // BEARER: Authorization: Bearer {token}
    // HEADER: 自定义 Header（格式 "HeaderName: Value"）

    // 超时：连接 10s，读取 30s
}
```

### 3.3 工具白名单过滤
```kotlin
class McpToolFilter {
    /**
     * 根据 mcp_servers.tool_whitelist_json 过滤可用工具
     * 白名单为空时允许所有工具
     */
    fun filterTools(
        tools: List<McpTool>,
        whitelist: List<String>
    ): List<McpTool>
}
```

## 4. MCP 调用编排

### 4.1 McpOrchestrator
```kotlin
class McpOrchestrator @Inject constructor(
    private val mcpClient: McpClient,
    private val configRepository: ConfigRepository,
    private val mcpCache: McpCache,
    private val mcpResultTrimmer: McpResultTrimmer,
    private val mcpPrivacyFilter: McpPrivacyFilter,
    private val appPreferencesDataStore: AppPreferencesDataStore
) {
    /**
     * 执行 AI 请求的 MCP 查询
     * 1. 检查联网权限
     * 2. 获取已启用的 MCP 服务
     * 3. 检查缓存
     * 4. 隐私过滤查询内容
     * 5. 选择合适的工具并调用
     * 6. 裁剪结果
     * 7. 写入缓存
     * 8. 返回精简文本
     */
    suspend fun executeQueries(queries: List<String>): McpExecutionResult

    /**
     * 单条查询执行
     */
    private suspend fun executeSingleQuery(
        query: String,
        servers: List<McpServerEntity>
    ): Result<String>
}

sealed interface McpExecutionResult {
    data class Success(
        val results: List<McpQueryResult>,
        val summary: String           // 合并后的精简文本
    ) : McpExecutionResult

    data class PartialSuccess(
        val results: List<McpQueryResult>,
        val failures: List<String>,
        val summary: String
    ) : McpExecutionResult

    data class AllFailed(val errors: List<String>) : McpExecutionResult
    data object NetworkDisabled : McpExecutionResult
    data object NoServersConfigured : McpExecutionResult
}

data class McpQueryResult(
    val query: String,
    val serverName: String,
    val toolName: String,
    val rawResult: String,
    val trimmedResult: String
)
```

### 4.2 查询路由策略
```kotlin
class McpQueryRouter {
    /**
     * 根据查询内容选择最合适的 MCP 服务和工具
     * - 优先匹配工具描述与查询意图
     * - 多个服务可用时，按优先级选择
     * - 同一查询只发往一个服务
     */
    fun selectServerAndTool(
        query: String,
        servers: List<McpServerEntity>,
        toolsByServer: Map<String, List<McpTool>>
    ): Pair<McpServerEntity, McpTool>?
}
```

## 5. 结果裁剪

### 5.1 McpResultTrimmer
```kotlin
class McpResultTrimmer {
    /**
     * 裁剪 MCP 返回结果，控制注入 Prompt 的 token 量
     * - 单条结果最多 2000 字符
     * - 多条结果合并后最多 4000 字符
     * - 去除 HTML 标签
     * - 去除重复内容
     * - 保留来源 URL（如有）
     */
    fun trim(rawResult: String, maxChars: Int = 2000): String

    fun mergeResults(results: List<McpQueryResult>, maxTotalChars: Int = 4000): String
}
```

## 6. 隐私保护

### 6.1 McpPrivacyFilter
```kotlin
class McpPrivacyFilter {
    /**
     * 对发送给 MCP 的查询进行隐私过滤
     * - 默认发送任务摘要而非完整历史
     * - 去除可能的个人信息（姓名、电话等）
     * - 查询内容尽量精简
     */
    fun sanitizeQuery(query: String): String

    /**
     * 生成 MCP 调用记录摘要（供用户在设置中查看）
     */
    fun buildCallSummary(
        query: String,
        serverName: String,
        toolName: String,
        resultPreview: String
    ): McpCallSummary
}

data class McpCallSummary(
    val timestamp: Long,
    val serverName: String,
    val toolName: String,
    val queryPreview: String,       // 前 100 字符
    val resultPreview: String,      // 前 200 字符
    val purpose: String             // 调用目的
)
```

## 7. 缓存策略

### 7.1 McpCache
```kotlin
class McpCache {
    // 内存缓存，LRU，最多 50 条
    // 缓存 key = hash(serverName + toolName + query)
    // TTL = 30 分钟
    // 避免短时间内对同一查询重复请求

    fun get(serverName: String, toolName: String, query: String): String?
    fun put(serverName: String, toolName: String, query: String, result: String)
    fun clear()
}
```

## 8. MCP 调用日志

### 8.1 存储
MCP 调用记录存储在 `heartbeat_logs.mcp_summary` 和独立的内存列表中：

```kotlin
class McpCallLogger {
    // 最近 20 条调用记录（内存中）
    // 供 Settings 页面展示
    private val recentCalls = mutableListOf<McpCallSummary>()

    fun log(summary: McpCallSummary)
    fun getRecent(): List<McpCallSummary>
}
```

## 9. 与 AI 编排层的集成点

### 9.1 Planner 中的 MCP 流程
```
PlannerPrompt 第一轮调用
  ↓
AI 输出 should_use_mcp=true, mcp_queries=[...]
  ↓
McpOrchestrator.executeQueries(queries)
  ↓
结果注入 PlannerPrompt 第二轮调用
  ↓
AI 输出最终计划（不再请求 MCP）
```

### 9.2 Heartbeat 中的 MCP 流程
```
HeartbeatPrompt 第一轮调用
  ↓
AI 输出 should_use_mcp=true, mcp_queries=[...]
  ↓
McpOrchestrator.executeQueries(queries)
  ↓
结果注入 HeartbeatPrompt 第二轮调用
  ↓
AI 输出最终通知决策
```

### 9.3 约束
- 每次 Planner / Heartbeat 最多一轮 MCP 调用
- mcp_queries 最多 3 条
- MCP 全部失败时，AI 必须给出保底结果
- 第二轮调用中 should_use_mcp 必须为 false（客户端强制）

## 10. 错误处理

```kotlin
sealed interface McpError {
    data object NetworkDisabled : McpError           // 用户未开启联网
    data object NoServersConfigured : McpError        // 无可用服务
    data class ConnectionFailed(val server: String, val cause: String) : McpError
    data class AuthFailed(val server: String) : McpError
    data class ToolNotFound(val toolName: String) : McpError
    data class ToolCallFailed(val toolName: String, val error: String) : McpError
    data class Timeout(val server: String) : McpError
}
```

- 连接失败：跳过该服务，尝试下一个
- 认证失败：标记该服务异常，提示用户检查配置
- 工具调用失败：记录错误，返回空结果
- 超时：记录错误，返回空结果
- 所有服务都失败：返回 AllFailed，AI 走保底路径

## 11. 验收标准
- [ ] 可成功连接远程 HTTP MCP 服务并列出工具
- [ ] 可成功调用 MCP 工具并获取结果
- [ ] 工具白名单过滤正确
- [ ] 结果裁剪控制在 token 预算内
- [ ] 缓存命中时不重复请求
- [ ] 联网开关关闭时拒绝所有 MCP 调用
- [ ] MCP 失败时 AI 编排层可正常降级
- [ ] 调用记录可在 Settings 中查看
- [ ] 认证信息加密存储

## 12. 依赖关系
- 前置：模块 01（骨架）、模块 02（数据层，McpServerEntity）
- 协作：模块 04（AI 编排层调用 MCP）
- 被依赖：模块 06（心跳中可能调用 MCP）
