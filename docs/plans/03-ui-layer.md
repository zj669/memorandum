# 模块 03 - UI 层（页面与导航）

## 1. 目标
实现所有页面的 UI 层，包括 Composable 组件、ViewModel、状态管理。采用 Material 3 设计语言，结构化卡片为主要展示形式。

## 2. 设计原则
- 单 Activity + Compose Navigation
- 每个页面一个 ViewModel，通过 Hilt 注入
- UI State 使用 sealed interface 建模
- 单向数据流：Event -> ViewModel -> UiState -> Composable
- 公共组件抽取到 `ui/common/`

## 3. 公共组件

### 3.1 组件清单
```
ui/common/
├── MemoCard.kt                # 通用卡片容器
├── StatusChip.kt              # 状态标签（TaskStatus 对应颜色）
├── RiskBadge.kt               # 风险等级徽标（0-3）
├── PriorityIndicator.kt       # 优先级指示器（1-5）
├── TimeBlockCard.kt           # 时间块卡片
├── StepItem.kt                # 步骤列表项（可勾选）
├── PrepItem.kt                # 准备项列表项
├── EmptyState.kt              # 空状态占位
├── LoadingState.kt            # 加载状态
├── ErrorState.kt              # 错误状态（带重试）
├── EntryTypeSelector.kt       # 类型选择器（5 种固定类型）
├── ImageAttachmentRow.kt      # 图片附件横向列表
├── ConfirmDialog.kt           # 通用确认弹窗
└── DateTimePicker.kt          # 日期时间选择器封装
```

### 3.2 MemoCard
```kotlin
@Composable
fun MemoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```

### 3.3 EntryTypeSelector
```kotlin
@Composable
fun EntryTypeSelector(
    selected: EntryType,
    onSelect: (EntryType) -> Unit
)
// 横向排列 5 个 FilterChip，每个对应一个 EntryType
// 图标 + 文字：TASK(✓) GOAL(🎯) MEMO(📝) REFERENCE(📎) REMINDER(🔔)
```

## 4. Today 页面

### 4.1 UiState
```kotlin
data class TodayUiState(
    val currentDate: String,
    val lastHeartbeatStatus: HeartbeatStatus?,
    val topRecommendation: TaskBrief?,
    val todayBlocks: List<ScheduleBlockWithTask>,
    val riskAlerts: List<TaskBrief>,
    val pendingClarification: ClarificationInfo?,
    val recentNotifications: List<NotificationBrief>,
    val isLoading: Boolean = false
)

data class TaskBrief(
    val taskId: String,
    val title: String,
    val status: TaskStatus,
    val nextAction: String?,
    val riskLevel: Int,
    val deadlineAt: Long?
)

data class ScheduleBlockWithTask(
    val block: ScheduleBlockEntity,
    val taskTitle: String,
    val taskStatus: TaskStatus
)

data class HeartbeatStatus(
    val checkedAt: Long,
    val shouldNotify: Boolean,
    val reason: String
)

data class ClarificationInfo(
    val entryId: String,
    val question: String,
    val reason: String
)
```

### 4.2 ViewModel
```kotlin
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val scheduleBlockDao: ScheduleBlockDao,
    private val notificationRepository: NotificationRepository,
    private val heartbeatLogDao: HeartbeatLogDao,
    private val entryRepository: EntryRepository
) : ViewModel() {

    val uiState: StateFlow<TodayUiState>

    fun onTaskClick(taskId: String)          // 导航到详情
    fun onClarificationAnswer(entryId: String, answer: String)
    fun onClarificationSkip(entryId: String)
    fun onNotificationClick(notificationId: String)
    fun onCreateEntry()                       // 导航到新建页
}
```

### 4.3 页面布局
```
TodayScreen
├── TopBar: 日期 + 心跳状态指示灯 + 新建按钮(FAB)
├── LazyColumn
│   ├── TopRecommendationCard     (如果有首要推荐任务)
│   ├── "今日安排" Section
│   │   └── TimeBlockCard * N     (按时间排序)
│   ├── "风险提醒" Section         (如果有)
│   │   └── RiskAlertCard * N
│   ├── "待回答" Section           (如果有待补充提问)
│   │   └── ClarificationCard
│   └── "最近通知" Section
│       └── NotificationBriefCard * N
└── EmptyState                     (无任何内容时)
```

## 5. Entry 新建页

### 5.1 UiState
```kotlin
data class EntryUiState(
    val selectedType: EntryType = EntryType.TASK,
    val text: String = "",
    val imageUris: List<Uri> = emptyList(),
    val deadline: Long? = null,
    val priority: Int? = null,
    val estimatedMinutes: Int? = null,
    val isSaving: Boolean = false,
    val saveResult: SaveResult? = null
)

sealed interface SaveResult {
    data class Success(val entryId: String) : SaveResult
    data class Error(val message: String) : SaveResult
}
```

### 5.2 ViewModel
```kotlin
@HiltViewModel
class EntryViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val planningOrchestrator: PlanningOrchestrator
) : ViewModel() {

    val uiState: StateFlow<EntryUiState>

    fun onTypeSelected(type: EntryType)
    fun onTextChanged(text: String)
    fun onImagesAdded(uris: List<Uri>)
    fun onImageRemoved(uri: Uri)
    fun onDeadlineSet(timestamp: Long?)
    fun onPrioritySet(priority: Int?)
    fun onEstimatedMinutesSet(minutes: Int?)
    fun onSubmit()                            // 保存 + 触发异步规划
}
```

### 5.3 页面布局
```
EntryScreen
├── TopBar: "新建条目" + 关闭按钮
├── EntryTypeSelector              (横向 5 个 Chip)
├── TextField                      (多行文本输入，自动聚焦)
├── ImageAttachmentRow             (横向滚动，末尾 + 号添加)
├── 可选字段区 (可折叠)
│   ├── 截止时间 Picker
│   ├── 优先级 Slider (1-5)
│   └── 预计时长输入 (分钟)
└── 提交按钮 (底部固定)
```

### 5.4 图片选择
- 使用 `ActivityResultContracts.PickMultipleVisualMedia`
- 选择后复制到应用私有目录，保存内部 URI
- 最多附加 5 张图片

## 6. Tasks 列表页

### 6.1 UiState
```kotlin
data class TasksUiState(
    val selectedFilter: TaskStatusFilter = TaskStatusFilter.ACTIVE,
    val sortBy: TaskSortBy = TaskSortBy.UPDATED,
    val searchQuery: String = "",
    val tasks: List<TaskListItem> = emptyList(),
    val isLoading: Boolean = false
)

enum class TaskStatusFilter {
    ACTIVE,    // INBOX + PLANNED + DOING + BLOCKED
    INBOX, PLANNED, DOING, BLOCKED, DONE, ALL
}

enum class TaskSortBy { UPDATED, DEADLINE, RISK }

data class TaskListItem(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val riskLevel: Int,
    val nextAction: String?,
    val deadlineAt: Long?,
    val planReady: Boolean,
    val lastProgressAt: Long?
)
```

### 6.2 ViewModel
```kotlin
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    val uiState: StateFlow<TasksUiState>

    fun onFilterChanged(filter: TaskStatusFilter)
    fun onSortChanged(sort: TaskSortBy)
    fun onSearchQueryChanged(query: String)
    fun onTaskClick(taskId: String)
}
```

### 6.3 页面布局
```
TasksScreen
├── TopBar: "任务" + 搜索图标
├── SearchBar (展开时显示)
├── FilterChipRow: ACTIVE / INBOX / PLANNED / DOING / BLOCKED / DONE
├── SortSelector: 最近更新 / 截止时间 / 风险等级
├── LazyColumn
│   └── TaskListCard * N
│       ├── StatusChip + Title
│       ├── NextAction (单行)
│       ├── RiskBadge + Deadline
│       └── PlanReady 指示
└── EmptyState
```

## 7. Task Detail 详情页

### 7.1 UiState
```kotlin
data class TaskDetailUiState(
    val task: TaskEntity? = null,
    val entry: EntryEntity? = null,
    val steps: List<PlanStepEntity> = emptyList(),
    val scheduleBlocks: List<ScheduleBlockEntity> = emptyList(),
    val prepItems: List<PrepItemEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isReplanningInProgress: Boolean = false
)
```

### 7.2 ViewModel
```kotlin
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val entryRepository: EntryRepository,
    private val planStepDao: PlanStepDao,
    private val scheduleBlockDao: ScheduleBlockDao,
    private val prepItemDao: PrepItemDao,
    private val notificationRepository: NotificationRepository,
    private val planningOrchestrator: PlanningOrchestrator
) : ViewModel() {

    private val taskId: String = savedStateHandle["taskId"]!!
    val uiState: StateFlow<TaskDetailUiState>

    fun onStatusChange(newStatus: TaskStatus)
    fun onStepStatusChange(stepId: String, newStatus: StepStatus)
    fun onPrepStatusChange(prepId: String, newStatus: PrepStatus)
    fun onScheduleAccepted(blockId: String)
    fun onReplan()                            // 重新触发 AI 规划
}
```

### 7.3 页面布局
```
TaskDetailScreen
├── TopBar: 返回 + 任务标题 + 状态变更菜单
├── LazyColumn
│   ├── 基础信息卡
│   │   ├── 类型 + 状态 + 风险等级
│   │   ├── 原始输入文本
│   │   ├── 截止时间 / 优先级 / 预计时长
│   │   └── 图片附件 (可点击放大)
│   ├── AI 摘要卡
│   │   ├── Summary 文本
│   │   └── 下一步动作 (高亮)
│   ├── 步骤列表卡
│   │   └── StepItem * N (可勾选切换状态)
│   ├── 时间安排卡
│   │   └── TimeBlockCard * N (显示接受/拒绝)
│   ├── 准备项卡
│   │   └── PrepItem * N (可勾选)
│   ├── 风险说明卡 (如果有)
│   └── 历史通知卡
│       └── NotificationItem * N
├── 底部操作栏
│   ├── 重新规划按钮
│   └── 状态快捷切换
```

## 8. Memory 页面

### 8.1 UiState
```kotlin
data class MemoryUiState(
    val selectedType: MemoryType? = null,     // null = 全部
    val memories: List<MemoryEntity> = emptyList(),
    val userProfile: UserProfileEntity? = null,
    val isLoading: Boolean = false
)
```

### 8.2 ViewModel
```kotlin
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val userProfileDao: UserProfileDao
) : ViewModel() {

    val uiState: StateFlow<MemoryUiState>

    fun onTypeFilterChanged(type: MemoryType?)
    fun onDeleteMemory(memoryId: String)
}
```

### 8.3 页面布局
```
MemoryScreen
├── TopBar: "AI 记忆"
├── FilterChipRow: 全部 / PREFERENCE / PATTERN / LONG_TERM_GOAL / TASK_CONTEXT / PREP_TEMPLATE
├── 用户画像摘要卡 (折叠展开)
│   └── 关键画像字段展示
├── LazyColumn
│   └── MemoryCard * N
│       ├── Type 标签 + Subject
│       ├── Content 正文
│       ├── Confidence 进度条
│       ├── 证据数量 + 最近使用时间
│       └── 删除按钮 (滑动或长按)
└── EmptyState
```

## 9. Notifications 页面

### 9.1 UiState
```kotlin
data class NotificationsUiState(
    val notifications: List<NotificationDisplayItem> = emptyList(),
    val isLoading: Boolean = false
)

data class NotificationDisplayItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val taskRef: String?,
    val createdAt: Long,
    val status: NotificationStatus     // UNREAD / CLICKED / DISMISSED / SNOOZED
)

enum class NotificationStatus { UNREAD, CLICKED, DISMISSED, SNOOZED }
```

### 9.2 页面布局
```
NotificationsScreen
├── TopBar: "通知历史"
├── LazyColumn
│   └── NotificationCard * N
│       ├── Type 图标 + Title
│       ├── Body 正文
│       ├── 时间 + 状态标签
│       └── 关联任务 (可点击跳转)
└── EmptyState
```

## 10. Settings 页面

### 10.1 UiState
```kotlin
data class SettingsUiState(
    val llmConfigs: List<LlmConfigEntity> = emptyList(),
    val mcpServers: List<McpServerEntity> = emptyList(),
    val heartbeatFrequency: HeartbeatFrequency = HeartbeatFrequency.MEDIUM,
    val quietHoursStart: String = "23:00",
    val quietHoursEnd: String = "07:00",
    val allowNetworkAccess: Boolean = false
)
```

### 10.2 子页面
```
SettingsScreen
├── "AI 模型" Section
│   ├── 已配置模型列表 (点击编辑)
│   └── 添加模型按钮 -> ModelConfigScreen
├── "MCP 服务" Section
│   ├── 已配置服务列表 (点击编辑，开关启用)
│   └── 添加服务按钮 -> McpConfigScreen
├── "心跳与通知" Section
│   ├── 心跳频率选择 (LOW / MEDIUM / HIGH)
│   ├── 静默时段设置
│   └── 通知权限状态
├── "隐私" Section
│   ├── 允许联网开关
│   └── 最近 MCP 调用记录 (可查看)
└── "数据" Section
    └── 清除所有数据 (确认弹窗)
```

### 10.3 ModelConfigScreen
```kotlin
data class ModelConfigUiState(
    val providerName: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val supportsImage: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: TestResult? = null
)
```

```
ModelConfigScreen
├── TopBar: "配置模型" + 保存按钮
├── 服务商名称输入
├── API 地址输入
├── 模型名称输入
├── API Key 输入 (密码模式)
├── 支持图片开关
└── 测试连接按钮 + 结果显示
```

### 10.4 McpConfigScreen
```
McpConfigScreen
├── TopBar: "配置 MCP" + 保存按钮
├── 服务名称输入
├── HTTP 地址输入
├── 认证方式选择 (NONE / BEARER / HEADER)
├── 认证信息输入 (密码模式)
├── 工具白名单 (可选，逗号分隔)
└── 测试连接按钮 + 结果显示
```

## 11. Clarification 交互

### 11.1 实现方式
使用 ModalBottomSheet：

```kotlin
@Composable
fun ClarificationSheet(
    question: String,
    reason: String,
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
)
```

### 11.2 布局
```
ClarificationSheet (ModalBottomSheet)
├── 问题文本 (大字)
├── 原因说明 (小字灰色)
├── 回答输入框
├── 按钮行
│   ├── 跳过 (OutlinedButton)
│   └── 提交 (FilledButton)
```

## 12. 主题与样式

### 12.1 配色
- 使用 Material 3 Dynamic Color（Android 12+）
- 低版本回退到固定配色方案
- 支持深色模式

### 12.2 字体
- 使用系统默认字体
- 标题：titleLarge / titleMedium
- 正文：bodyLarge / bodyMedium
- 标签：labelMedium / labelSmall

### 12.3 间距
- 卡片间距：12dp
- 卡片内边距：16dp
- Section 间距：24dp

## 13. 验收标准
- [ ] 4 个底部 Tab 页面完整可交互
- [ ] Entry 页面可创建条目并保存到本地
- [ ] Tasks 列表支持筛选、排序、搜索
- [ ] Task Detail 展示所有卡片区域
- [ ] Memory 页面展示记忆列表和用户画像
- [ ] Settings 页面可配置 LLM 和 MCP
- [ ] Clarification Sheet 可正常弹出和交互
- [ ] 深色模式正常显示
- [ ] 图片选择和展示正常

## 14. 依赖关系
- 前置：模块 01（骨架）、模块 02（数据层）
- 被依赖：模块 04（AI 编排，需要 UI 触发规划和展示结果）
