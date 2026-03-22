# 模块 08 - 设置与配置

## 1. 目标
实现完整的设置管理系统，包括 LLM API 配置、MCP 服务配置、心跳频率、静默时段、隐私控制和数据管理。提供连接测试能力，确保用户配置可用。

## 2. 架构

```
ui/settings/
├── SettingsScreen.kt             # 设置主页面
├── SettingsViewModel.kt
├── ModelConfigScreen.kt          # LLM 配置页
├── ModelConfigViewModel.kt
├── McpConfigScreen.kt            # MCP 配置页
├── McpConfigViewModel.kt
└── McpCallHistoryScreen.kt       # MCP 调用历史

domain/usecase/config/
├── TestLlmConnectionUseCase.kt   # 测试 LLM 连接
├── TestMcpConnectionUseCase.kt   # 测试 MCP 连接
├── ExportDataUseCase.kt          # 数据导出（后续）
└── ClearDataUseCase.kt           # 数据清理
```

## 3. LLM 配置

### 3.1 ModelConfigViewModel
```kotlin
@HiltViewModel
class ModelConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val configRepository: ConfigRepository,
    private val testLlmConnectionUseCase: TestLlmConnectionUseCase,
    private val cryptoHelper: CryptoHelper
) : ViewModel() {

    // 编辑模式：configId 非空时为编辑，否则为新建
    private val configId: String? = savedStateHandle["configId"]

    val uiState: StateFlow<ModelConfigUiState>

    fun onProviderNameChanged(name: String)
    fun onBaseUrlChanged(url: String)
    fun onModelNameChanged(model: String)
    fun onApiKeyChanged(key: String)
    fun onSupportsImageChanged(supports: Boolean)

    /**
     * 测试连接
     * 发送一个简单的 chat completion 请求验证配置
     */
    fun onTestConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            val result = testLlmConnectionUseCase.test(
                baseUrl = uiState.value.baseUrl,
                apiKey = uiState.value.apiKey,
                modelName = uiState.value.modelName
            )
            _uiState.update { it.copy(isTesting = false, testResult = result) }
        }
    }

    /**
     * 保存配置
     * API Key 加密后存储
     */
    fun onSave()

    /**
     * 删除配置
     */
    fun onDelete()
}

data class ModelConfigUiState(
    val configId: String? = null,
    val providerName: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val supportsImage: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val isSaving: Boolean = false,
    val isValid: Boolean = false          // 表单校验
)

sealed interface ConnectionTestResult {
    data class Success(val responsePreview: String) : ConnectionTestResult
    data class Failed(val error: String) : ConnectionTestResult
}
```

### 3.2 TestLlmConnectionUseCase
```kotlin
class TestLlmConnectionUseCase @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    /**
     * 发送测试请求：
     * POST {baseUrl}/v1/chat/completions
     * body: { model: modelName, messages: [{ role: "user", content: "Hi" }], max_tokens: 10 }
     *
     * 成功：返回模型响应片段
     * 失败：返回错误信息（401=Key无效, 404=URL错误, timeout=连接超时）
     */
    suspend fun test(baseUrl: String, apiKey: String, modelName: String): ConnectionTestResult
}
```

### 3.3 预设模板
```kotlin
object LlmPresets {
    val presets = listOf(
        LlmPreset(
            name = "OpenAI",
            baseUrl = "https://api.openai.com",
            suggestedModels = listOf("gpt-4o", "gpt-4o-mini"),
            supportsImage = true
        ),
        LlmPreset(
            name = "Anthropic (OpenAI 兼容)",
            baseUrl = "https://api.anthropic.com/v1",
            suggestedModels = listOf("claude-sonnet-4-20250514"),
            supportsImage = true
        ),
        LlmPreset(
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            suggestedModels = listOf("deepseek-chat", "deepseek-reasoner"),
            supportsImage = false
        ),
        LlmPreset(
            name = "自定义",
            baseUrl = "",
            suggestedModels = emptyList(),
            supportsImage = false
        )
    )
}
```

## 4. MCP 配置

### 4.1 McpConfigViewModel
```kotlin
@HiltViewModel
class McpConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val configRepository: ConfigRepository,
    private val testMcpConnectionUseCase: TestMcpConnectionUseCase,
    private val cryptoHelper: CryptoHelper
) : ViewModel() {

    val uiState: StateFlow<McpConfigUiState>

    fun onNameChanged(name: String)
    fun onBaseUrlChanged(url: String)
    fun onAuthTypeChanged(type: AuthType)
    fun onAuthValueChanged(value: String)
    fun onToolWhitelistChanged(whitelist: String)   // 逗号分隔

    /**
     * 测试连接
     * 调用 tools/list 验证连接和认证
     */
    fun onTestConnection()

    fun onSave()
    fun onDelete()
}

data class McpConfigUiState(
    val serverId: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val authType: AuthType = AuthType.NONE,
    val authValue: String = "",
    val toolWhitelist: String = "",
    val isTesting: Boolean = false,
    val testResult: McpTestResult? = null,
    val isSaving: Boolean = false,
    val isValid: Boolean = false
)

enum class AuthType { NONE, BEARER, HEADER }

sealed interface McpTestResult {
    data class Success(val tools: List<String>) : McpTestResult   // 返回可用工具列表
    data class Failed(val error: String) : McpTestResult
}
```

### 4.2 TestMcpConnectionUseCase
```kotlin
class TestMcpConnectionUseCase @Inject constructor(
    private val mcpClient: McpClient
) {
    /**
     * 调用 tools/list 验证：
     * - 连接是否可达
     * - 认证是否有效
     * - 返回可用工具列表
     */
    suspend fun test(
        baseUrl: String,
        authType: String,
        authValue: String
    ): McpTestResult
}
```

## 5. 心跳与通知设置

### 5.1 设置项
```kotlin
// 在 SettingsViewModel 中管理

fun onHeartbeatFrequencyChanged(frequency: HeartbeatFrequency) {
    viewModelScope.launch {
        appPreferencesDataStore.updateHeartbeatFrequency(frequency)
        heartbeatScheduleManager.scheduleHeartbeat(frequency)
    }
}

fun onQuietHoursChanged(start: String, end: String) {
    viewModelScope.launch {
        appPreferencesDataStore.updateQuietHours(start, end)
    }
}

fun onAllowNetworkChanged(allow: Boolean) {
    viewModelScope.launch {
        appPreferencesDataStore.updateAllowNetwork(allow)
    }
}
```

### 5.2 通知权限引导
```kotlin
class NotificationPermissionHelper {
    /**
     * 检查通知权限状态
     * Android 13+ 需要 POST_NOTIFICATIONS 运行时权限
     */
    fun checkPermission(context: Context): PermissionStatus

    /**
     * 检查精确闹钟权限
     * Android 12+ 需要 SCHEDULE_EXACT_ALARM
     */
    fun checkExactAlarmPermission(context: Context): PermissionStatus

    /**
     * 引导用户到系统设置页
     */
    fun openNotificationSettings(context: Context)
    fun openExactAlarmSettings(context: Context)
}

enum class PermissionStatus { GRANTED, DENIED, NOT_REQUIRED }
```

## 6. MCP 调用历史

### 6.1 McpCallHistoryScreen
```kotlin
// 展示最近的 MCP 调用记录
// 数据来源：McpCallLogger.getRecent()

@Composable
fun McpCallHistoryScreen(
    calls: List<McpCallSummary>,
    onBack: () -> Unit
) {
    // LazyColumn
    //   └── McpCallCard * N
    //       ├── 时间 + 服务名 + 工具名
    //       ├── 查询内容预览
    //       ├── 结果预览
    //       └── 调用目的
}
```

## 7. 数据管理

### 7.1 ClearDataUseCase
```kotlin
class ClearDataUseCase @Inject constructor(
    private val database: MemorandumDatabase,
    private val appPreferencesDataStore: AppPreferencesDataStore,
    private val heartbeatScheduleManager: HeartbeatScheduleManager,
    private val alarmScheduler: AlarmScheduler
) {
    /**
     * 清除所有数据
     * 1. 取消所有 Alarm
     * 2. 取消心跳调度
     * 3. 清空数据库
     * 4. 重置 DataStore
     * 5. 清除图片缓存
     */
    suspend fun clearAll() {
        heartbeatScheduleManager.pauseHeartbeat()
        // 取消所有 Alarm（遍历活跃任务）
        database.clearAllTables()
        appPreferencesDataStore.reset()
    }

    /**
     * 仅清除记忆数据
     */
    suspend fun clearMemories() {
        database.memoryDao().deleteAll()
        database.userProfileDao().deleteAll()
    }

    /**
     * 仅清除通知历史
     */
    suspend fun clearNotifications() {
        database.notificationDao().deleteAll()
        database.heartbeatLogDao().deleteAll()
    }
}
```

## 8. Settings 页面布局

```
SettingsScreen
├── TopBar: "设置"
├── LazyColumn
│   ├── Section: "AI 模型"
│   │   ├── 已配置模型列表
│   │   │   └── ModelCard * N
│   │   │       ├── 服务商 + 模型名
│   │   │       ├── 多模态支持标签
│   │   │       └── 点击编辑 -> ModelConfigScreen
│   │   └── "添加模型" 按钮
│   │
│   ├── Section: "MCP 服务"
│   │   ├── 已配置服务列表
│   │   │   └── McpCard * N
│   │   │       ├── 服务名 + URL
│   │   │       ├── 启用/禁用开关
│   │   │       └── 点击编辑 -> McpConfigScreen
│   │   ├── "添加服务" 按钮
│   │   └── "查看调用历史" -> McpCallHistoryScreen
│   │
│   ├── Section: "心跳与通知"
│   │   ├── 心跳频率: SegmentedButton (低/中/高)
│   │   ├── 静默时段: 开始时间 + 结束时间
│   │   ├── 通知权限状态 + 引导按钮
│   │   └── 精确闹钟权限状态 + 引导按钮
│   │
│   ├── Section: "隐私"
│   │   ├── 允许联网开关
│   │   └── 说明文字: "关闭后 AI 将不会调用 MCP 搜索"
│   │
│   └── Section: "数据管理"
│       ├── "清除记忆数据" (确认弹窗)
│       ├── "清除通知历史" (确认弹窗)
│       └── "清除所有数据" (二次确认弹窗，红色警告)
```

## 9. 表单校验规则

### 9.1 LLM 配置校验
```kotlin
fun validateLlmConfig(state: ModelConfigUiState): Boolean {
    return state.providerName.isNotBlank()
        && state.baseUrl.isNotBlank()
        && state.baseUrl.startsWith("http")
        && state.modelName.isNotBlank()
        && state.apiKey.isNotBlank()
}
```

### 9.2 MCP 配置校验
```kotlin
fun validateMcpConfig(state: McpConfigUiState): Boolean {
    val baseValid = state.name.isNotBlank()
        && state.baseUrl.isNotBlank()
        && state.baseUrl.startsWith("http")

    val authValid = when (state.authType) {
        AuthType.NONE -> true
        AuthType.BEARER -> state.authValue.isNotBlank()
        AuthType.HEADER -> state.authValue.contains(":")   // "HeaderName: Value" 格式
    }

    return baseValid && authValid
}
```

### 9.3 静默时段校验
```kotlin
fun validateQuietHours(start: String, end: String): Boolean {
    val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    return timeRegex.matches(start) && timeRegex.matches(end)
}
```

## 10. 首次使用引导

### 10.1 Onboarding 流程
```kotlin
class OnboardingManager @Inject constructor(
    private val appPreferencesDataStore: AppPreferencesDataStore
) {
    /**
     * 首次打开 App 时：
     * 1. 检查是否已完成引导
     * 2. 未完成则引导用户配置 LLM
     * 3. 配置完成后标记引导完成
     * 4. 可选：引导配置 MCP 和通知权限
     */
    suspend fun isOnboardingNeeded(): Boolean
    suspend fun completeOnboarding()
}
```

### 10.2 引导页面
```
OnboardingScreen (简单的分步引导)
├── Step 1: 欢迎页 + 产品简介
├── Step 2: 配置 AI 模型 (必须)
│   └── 内嵌 ModelConfigScreen 的简化版
├── Step 3: 通知权限请求
└── Step 4: 完成，进入 Today 页面
```

## 11. 验收标准
- [ ] LLM 配置可新增、编辑、删除
- [ ] LLM 连接测试可正确返回成功/失败
- [ ] API Key 加密存储，界面显示为密码模式
- [ ] MCP 配置可新增、编辑、删除、启用/禁用
- [ ] MCP 连接测试可返回工具列表
- [ ] 心跳频率切换后 WorkManager 调度更新
- [ ] 静默时段设置可正常保存
- [ ] 联网开关可控制 MCP 调用
- [ ] 数据清理功能正确执行
- [ ] 首次使用引导流程完整
- [ ] 通知权限引导正常
- [ ] 表单校验阻止无效配置保存

## 12. 依赖关系
- 前置：模块 01（骨架）、模块 02（数据层）
- 协作：模块 04（LLM 客户端）、模块 05（MCP 客户端）、模块 06（心跳调度）
- 被依赖：所有需要 LLM/MCP 配置的模块
