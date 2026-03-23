# Phase 3: Entry, Tasks List, Task Detail UI

## Goal
实现录入页、任务列表页、任务详情页的完整 UI，包括 Today 页面的丰富内容展示。同时创建所有公共 UI 组件。

## Requirements

### 公共组件 (ui/common/)
- MemoCard: 通用卡片容器
- StatusChip: TaskStatus 对应颜色标签
- RiskBadge: 风险等级徽标 (0-3)
- PriorityIndicator: 优先级指示器 (1-5)
- TimeBlockCard: 时间块卡片
- StepItem: 步骤列表项（可勾选）
- PrepItem: 准备项列表项（可勾选）
- EmptyState: 空状态占位
- LoadingState: 加载状态
- ErrorState: 错误状态（带重试）
- EntryTypeSelector: 5种固定类型选择器
- ImageAttachmentRow: 图片附件横向列表

### Entry 新建页
- EntryTypeSelector 横向 5 个 Chip
- 多行文本输入（自动聚焦）
- 图片附件区（横向滚动，末尾+号添加，最多5张）
- 可折叠的可选字段区：截止时间 Picker、优先级 Slider(1-5)、预计时长输入
- 底部固定提交按钮
- 提交后保存到本地并触发异步规划（TODO占位）

### Tasks 列表页
- 状态筛选 FilterChipRow: ACTIVE / INBOX / PLANNED / DOING / BLOCKED / DONE
- 排序选择: 最近更新 / 截止时间 / 风险等级
- 搜索栏（可展开）
- LazyColumn 展示 TaskListCard（StatusChip + Title + NextAction + RiskBadge + Deadline）
- 点击跳转到详情页

### Task Detail 详情页
- 基础信息卡（类型+状态+风险+原始文本+截止时间+优先级+图片）
- AI 摘要卡（Summary + 下一步动作高亮）
- 步骤列表卡（StepItem 可勾选切换状态）
- 时间安排卡（TimeBlockCard）
- 准备项卡（PrepItem 可勾选）
- 风险说明卡
- 底部操作栏（重新规划按钮 + 状态快捷切换）

### Today 页面增强
- 顶部：日期 + 心跳状态指示灯 + FAB新建按钮
- TopRecommendationCard（首要推荐任务）
- 今日安排 Section（TimeBlockCard 列表）
- 风险提醒 Section
- 待回答 Section（ClarificationCard）
- 最近通知 Section

## Acceptance Criteria
- [ ] 所有公共组件创建完毕
- [ ] Entry 页面可选择类型、输入文本、添加图片、设置可选字段、提交
- [ ] Tasks 列表支持筛选、排序、搜索
- [ ] Task Detail 展示所有卡片区域
- [ ] Today 页面展示丰富内容
- [ ] 所有页面有 Loading/Empty/Error 状态
- [ ] 项目可编译通过

## Technical Notes
- 参考 docs/plans/03-ui-layer.md 获取详细设计
- 图片选择使用 ActivityResultContracts.PickMultipleVisualMedia
- 日期时间选择使用 Material 3 DatePicker/TimePicker
- ViewModel 中 Repository 调用用 TODO() 占位
- 使用已有的 Entity 和枚举类型（Phase 2A 已实现）
- 数据展示用 mock/preview 数据
