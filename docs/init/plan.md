# PLAN - Android 本地优先 AI 个人规划助手

## 1. 技术方案总览
### 1.1 技术栈
- 语言：Kotlin
- UI：Jetpack Compose
- 本地数据库：Room
- 本地配置：DataStore
- 后台任务：WorkManager
- 精确提醒：AlarmManager
- 本地通知：NotificationManager
- 网络层：Retrofit + OkHttp
- 图片输入：本地 URI + 多模态 API 透传
- 序列化：Kotlinx Serialization

### 1.2 架构分层
- `ui/`：页面、导航、状态展示
- `domain/`：规划、心跳、记忆更新等用例
- `data/`：Room、DataStore、LLM、MCP、通知、调度
- `ai/`：prompt 模板、JSON schema、AI 编排器

### 1.3 目录建议
- `ui/today/`
- `ui/entry/`
- `ui/tasks/`
- `ui/taskdetail/`
- `ui/memory/`
- `ui/notifications/`
- `ui/settings/`
- `domain/usecase/`
- `data/local/room/`
- `data/local/datastore/`
- `data/remote/llm/`
- `data/remote/mcp/`
- `ai/prompt/`
- `ai/schema/`
- `scheduler/`

## 2. 页面与导航设计
### 2.1 底部导航
第一版建议固定 4 个主 Tab：
- `Today`
- `Tasks`
- `Memory`
- `Settings`

### 2.2 次级页面
- `EntryScreen`
- `TaskDetailScreen`
- `NotificationsScreen`
- `ClarificationSheet`
- `ModelConfigScreen`
- `McpConfigScreen`

### 2.3 Today 页面结构
#### 顶部区域
- 当前日期
- 最近一次心跳状态
- 快速新建入口

#### 主体区域
- `Top Recommendation Card`
- `Today Schedule Blocks`
- `Risk Alerts`
- `Pending Clarification`
- `Recent Notifications`

### 2.4 Tasks 页面结构
- 状态筛选：`INBOX / PLANNED / DOING / BLOCKED / DONE`
- 排序：最近更新 / 截止时间 / 风险等级
- 搜索：按标题和摘要搜索本地任务

### 2.5 Task Detail 页面结构
- 基础信息卡
- AI 计划摘要卡
- 下一步动作卡
- 步骤列表卡
- 时间块卡
- 准备项卡
- 风险说明卡
- 历史通知卡

### 2.6 Entry 页面结构
- 类型选择器
- 文本输入框
- 图片附件区
- 可选约束区：截止时间、优先级、预计时长
- 提交按钮

### 2.7 Clarification 交互
- 只允许单题单答
- 用 Bottom Sheet 或 Dialog 承载
- 支持 `回答`、`跳过` 两个动作
- 回答和跳过都必须重新进入 Planner

## 3. 核心流程
### 3.1 新任务规划流程
1. 用户创建条目并手动选择 `EntryType`
2. 本地保存原始条目
3. Planner 读取：
   - 条目内容
   - 用户画像
   - 相关长期记忆
   - 历史相似任务
4. Planner 输出 `needs_clarification`
5. 若为 `true` 且该任务未提问过，则展示 1 个问题
6. 用户回答或跳过后，重新执行 Planner
7. Planner 判断是否需要 MCP
8. 若需要，则发起一次 MCP 调用并带回结果
9. Planner 输出最终 JSON 计划
10. 客户端校验 JSON 并写入本地数据库
11. 若有精确时间提醒，则注册 Alarm；否则进入心跳巡检

### 3.2 心跳流程
1. WorkManager 固定间隔触发
2. 汇总：
   - 未完成任务
   - 已规划但未执行任务
   - 即将截止任务
   - 最近通知历史
   - 当前用户画像
3. 调用 Heartbeat Prompt
4. 若需要 MCP，则执行一次工具调用再回填
5. Heartbeat 输出：
   - 是否通知
   - 通知文案
   - 通知类型
   - 关联任务
   - 决策原因
6. 客户端执行去重、安静时段、冷却时间判断
7. 符合条件则发送本地通知
8. 写入心跳日志

### 3.3 长期记忆更新流程
1. 在任务完成、计划被接受、通知被响应后触发
2. 汇总新的行为证据
3. 调用 Memory Prompt
4. 输出新增/更新/降权的记忆项
5. 写入 `memories` 与 `user_profile` 聚合视图

### 3.4 MCP 调用流程
1. AI 输出 `should_use_mcp=true`
2. 编排层从 `mcp_servers` 读取已启用服务
3. 组装 MCP 请求
4. 返回精简文本结果与来源摘要
5. 再次调用对应 Prompt 完成最终决策

## 4. 数据模型详细设计
### 4.1 `entries`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键，UUID |
| `type` | TEXT | `EntryType` |
| `text` | TEXT | 用户原始输入 |
| `created_at` | INTEGER | 创建时间戳 |
| `updated_at` | INTEGER | 更新时间戳 |
| `priority` | INTEGER | 1-5，可空 |
| `deadline_at` | INTEGER | 截止时间，可空 |
| `estimated_minutes` | INTEGER | 预计耗时，可空 |
| `image_uris_json` | TEXT | 图片 URI 数组 |
| `planning_state` | TEXT | `PlanningState` |
| `clarification_used` | INTEGER | 0/1 |
| `clarification_question` | TEXT | 最近一次问题，可空 |
| `clarification_answer` | TEXT | 最近一次回答，可空 |
| `last_planned_at` | INTEGER | 最近规划时间，可空 |

索引建议：
- `index_entries_type_created_at`
- `index_entries_planning_state_updated_at`
- `index_entries_deadline_at`

### 4.2 `tasks`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键 |
| `entry_id` | TEXT | 来源条目 ID |
| `title` | TEXT | AI 生成标题 |
| `status` | TEXT | `TaskStatus` |
| `summary` | TEXT | AI 摘要 |
| `goal_id` | TEXT | 所属目标，可空 |
| `next_action` | TEXT | 当前建议下一步 |
| `risk_level` | INTEGER | 0-3 |
| `plan_version` | INTEGER | 规划版本号 |
| `plan_ready` | INTEGER | 是否已有可用计划 |
| `last_heartbeat_at` | INTEGER | 最近被心跳检查时间 |
| `last_progress_at` | INTEGER | 最近推进时间 |
| `notification_cooldown_until` | INTEGER | 冷却到期时间 |

索引建议：
- `index_tasks_status_last_progress_at`
- `index_tasks_risk_level_status`
- `index_tasks_goal_id`

### 4.3 `schedule_blocks`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键 |
| `task_id` | TEXT | 关联任务 |
| `block_date` | TEXT | `YYYY-MM-DD` |
| `start_time` | TEXT | `HH:mm` |
| `end_time` | TEXT | `HH:mm` |
| `reason` | TEXT | AI 解释原因 |
| `source` | TEXT | `PLANNER` / `USER` |
| `accepted` | INTEGER | 用户是否接受 |
| `created_at` | INTEGER | 创建时间 |

索引建议：
- `index_schedule_blocks_task_id_block_date`
- `index_schedule_blocks_block_date_start_time`

### 4.4 `plan_steps`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键 |
| `task_id` | TEXT | 关联任务 |
| `step_index` | INTEGER | 步骤顺序 |
| `title` | TEXT | 步骤标题 |
| `description` | TEXT | 步骤详情 |
| `status` | TEXT | `TODO / DOING / DONE / SKIPPED` |
| `needs_mcp` | INTEGER | 是否依赖搜索 |
| `created_at` | INTEGER | 创建时间 |
| `updated_at` | INTEGER | 更新时间 |

### 4.5 `prep_items`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键 |
| `task_id` | TEXT | 关联任务 |
| `content` | TEXT | 准备项内容 |
| `status` | TEXT | `TODO / DONE / SKIPPED` |
| `source_memory_id` | TEXT | 如来自记忆模板则保存引用 |

### 4.6 `memories`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键 |
| `type` | TEXT | `MemoryType` |
| `subject` | TEXT | 记忆主题 |
| `content` | TEXT | 记忆正文 |
| `confidence` | REAL | 0-1 |
| `source_refs_json` | TEXT | 证据来源数组 |
| `evidence_count` | INTEGER | 证据数量 |
| `updated_at` | INTEGER | 更新时间 |
| `last_used_at` | INTEGER | 最近被 Planner 使用时间 |

索引建议：
- `index_memories_type_confidence`
- `index_memories_subject`

### 4.7 `user_profile`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 固定单行主键 |
| `profile_json` | TEXT | 聚合画像 JSON |
| `version` | INTEGER | 画像版本 |
| `updated_at` | INTEGER | 更新时间 |

说明：
- `profile_json` 存聚合结果，不存原始证据。
- 原始证据应保留在 `memories`、`notifications`、`task_events` 等表中。

### 4.8 `task_events`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键 |
| `task_id` | TEXT | 关联任务 |
| `event_type` | TEXT | `CREATED / PLANNED / STARTED / DONE / DROPPED / SNOOZED / ACCEPTED_PLAN / REJECTED_PLAN` |
| `payload_json` | TEXT | 扩展字段 |
| `created_at` | INTEGER | 事件时间 |

说明：
- 用于记忆归纳和行为分析。
- 这是长期画像积累的核心证据表。

### 4.9 `heartbeat_logs`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键 |
| `checked_at` | INTEGER | 检查时间 |
| `should_notify` | INTEGER | 是否通知 |
| `notification_type` | TEXT | `NotificationType`，可空 |
| `reason` | TEXT | 决策原因 |
| `task_ref` | TEXT | 关联任务，可空 |
| `used_mcp` | INTEGER | 是否调用 MCP |
| `mcp_summary` | TEXT | MCP 摘要，可空 |

### 4.10 `notifications`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键 |
| `type` | TEXT | `NotificationType` |
| `action_type` | TEXT | `NotificationActionType` |
| `title` | TEXT | 通知标题 |
| `body` | TEXT | 通知内容 |
| `task_ref` | TEXT | 任务引用，可空 |
| `created_at` | INTEGER | 创建时间 |
| `clicked_at` | INTEGER | 点击时间，可空 |
| `dismissed_at` | INTEGER | 忽略时间，可空 |
| `snoozed_until` | INTEGER | 稍后提醒时间，可空 |

索引建议：
- `index_notifications_task_ref_created_at`
- `index_notifications_type_created_at`

### 4.11 `llm_configs`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键 |
| `provider_name` | TEXT | 服务提供方名称 |
| `base_url` | TEXT | API 地址 |
| `model_name` | TEXT | 模型名 |
| `api_key_encrypted` | TEXT | 加密后的 key |
| `supports_image` | INTEGER | 是否支持多模态 |
| `updated_at` | INTEGER | 更新时间 |

### 4.12 `mcp_servers`
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT | 主键 |
| `name` | TEXT | 服务名称 |
| `base_url` | TEXT | 远程 HTTP 地址 |
| `auth_type` | TEXT | `NONE / BEARER / HEADER` |
| `auth_value_encrypted` | TEXT | 加密后的认证信息 |
| `enabled` | INTEGER | 是否启用 |
| `tool_whitelist_json` | TEXT | 允许调用的工具名单 |
| `updated_at` | INTEGER | 更新时间 |

## 5. AI Prompt 模块设计
我建议拆成 4 套 prompt，而不是一套大 prompt。

### 5.1 Planner Prompt
用途：对单个任务/目标进行完整规划。

#### 输入
- 当前条目
- 用户画像摘要
- 相关长期记忆
- 历史相似任务摘要
- 当前时间
- 可选的 MCP 搜索结果
- 是否已使用过补充提问

#### 输出 JSON 结构
```json
{
  "needs_clarification": true,
  "clarification_question": "这项任务最晚必须在哪一天完成？",
  "clarification_reason": "缺少截止信息，无法安排优先级",
  "should_use_mcp": false,
  "mcp_queries": [],
  "task_title": "准备面试",
  "summary": "为目标岗位准备面试材料与模拟练习",
  "steps": [
    {
      "index": 1,
      "title": "整理岗位信息",
      "description": "明确岗位要求、面试轮次与重点能力",
      "needs_mcp": false
    }
  ],
  "schedule_blocks": [
    {
      "date": "2026-03-22",
      "start": "20:00",
      "end": "21:00",
      "reason": "用户晚间适合资料整理"
    }
  ],
  "prep_items": [
    "准备简历终版",
    "整理项目案例"
  ],
  "risks": [
    "截止时间不明确"
  ],
  "notification_candidates": [
    {
      "type": "TIME_TO_START",
      "title": "该开始准备面试了",
      "body": "今晚 20:00 安排了资料整理",
      "action_type": "OPEN_TASK"
    }
  ]
}
```

#### 约束
- 若 `needs_clarification=true`，则本轮不得同时给出最终计划。
- 若已使用补充提问，则必须返回最终计划，不能再次提问。
- `schedule_blocks` 允许为空，但必须给出原因。
- `mcp_queries` 最多 3 条，且属于同一轮检索目的。

### 5.2 Clarifier Prompt
用途：只负责生成一个最高价值问题。

#### 输出 JSON
```json
{
  "ask": true,
  "question": "这项任务你希望优先追求速度还是质量？",
  "reason": "当前缺少规划偏好，无法判断是否拆分为更小步骤"
}
```

#### 约束
- 问题必须是一句话。
- 用户应能在短时间内回答。
- 不允许追问子问题。

### 5.3 Heartbeat Prompt
用途：周期性巡检是否要提醒用户。

#### 输出 JSON
```json
{
  "should_use_mcp": false,
  "mcp_queries": [],
  "should_notify": true,
  "notification": {
    "type": "DEADLINE_RISK",
    "action_type": "OPEN_TASK",
    "title": "这个任务有延期风险",
    "body": "距离截止只剩 2 天，但关键步骤还没开始"
  },
  "reason": "任务剩余时间不足，且近 48 小时没有推进",
  "task_ref": "task_123",
  "cooldown_hours": 8
}
```

#### 约束
- `should_notify=false` 时，`notification` 必须为 `null`。
- `cooldown_hours` 由 AI 给建议，最终由客户端裁剪。
- 对同一任务的提醒理由必须具体，不能只写“需要推进”。

### 5.4 Memory Prompt
用途：把行为证据沉淀为长期画像。

#### 输出 JSON
```json
{
  "new_memories": [
    {
      "type": "PREFERENCE",
      "subject": "工作时段偏好",
      "content": "用户更容易在晚间完成资料整理类任务",
      "confidence": 0.77,
      "source_refs": ["task_1", "task_8"]
    }
  ],
  "updates": [],
  "downgrades": []
}
```

#### 约束
- 只有存在足够证据时才新增记忆。
- 低置信度记忆优先更新而不是重复创建。
- `source_refs` 至少包含 1 个证据来源。

## 6. 通知与心跳策略
### 6.1 心跳实现策略
- 常规巡检：WorkManager
- 精确提醒：AlarmManager
- 客户端支持暂停心跳、调整频率和静默时段

### 6.2 客户端硬保护
1. 同一次心跳只允许发送 1 条通知。
2. 静默时段内默认不发送普通通知。
3. 同一任务在冷却时间内不得重复通知。
4. 标记完成的任务不得继续触发提醒。
5. 当用户连续忽略同类通知时，应提高发送阈值。

### 6.3 心跳频率建议
第一版可用 3 档配置：
- `LOW`：低频，适合轻提醒用户
- `MEDIUM`：默认
- `HIGH`：高频，但仍受系统限制

### 6.4 通知动作
建议首版支持：
- 打开任务详情
- 打开 Today
- 稍后提醒 1 小时
- 标记完成

## 7. 我做的工程决策
### 7.1 单次任务最多一次提问
这是产品硬约束，客户端记录 `clarification_used=true/false`，避免无限追问。

### 7.2 AI 全权规划，但客户端保留三类硬校验
1. JSON schema 校验
2. 时间字段合法性校验
3. 通知频率和去重校验

### 7.3 MCP 调用策略
- 每次 Planner / Heartbeat 最多允许一轮 MCP 调用。
- 若 MCP 失败，AI 仍需在无外部信息下给出保底计划。
- MCP 返回结果会本地缓存，避免短时间重复请求。
- MCP 返回内容进入 Prompt 前需要被裁剪与规范化。

### 7.4 图片处理策略
- 客户端只保存本地 URI。
- 调模型时按用户所选 API 要求组装图片输入。
- 若模型不支持图片，则降级为仅文本上下文。

## 8. 推荐开发顺序
### Phase 1：项目骨架
- 创建 Android 项目
- 搭建 Compose 导航
- 建立 Room / DataStore / Repository 基础设施

### Phase 2：本地数据与设置
- 完成本地表结构
- 完成 LLM 配置页
- 完成 MCP 配置页
- 完成通知权限与设置页

### Phase 3：录入与列表
- 新建条目页
- 条目列表 / 任务列表页
- 详情页
- 图片附件接入

### Phase 4：AI 编排
- 接入 Planner Prompt
- 接入 Clarifier Prompt
- 保存计划结果
- 展示步骤、排程、准备项

### Phase 5：MCP 工具调用
- 远程 HTTP MCP 客户端
- Planner 中的 MCP 决策与回填
- 失败回退逻辑

### Phase 6：心跳与通知
- WorkManager 心跳
- Heartbeat Prompt
- 本地通知发送与去重
- 通知动作处理
- 日志记录

### Phase 7：长期记忆
- Memory Prompt
- 用户画像聚合
- 任务历史与偏好沉淀
- 低置信度记忆更新与降权

## 9. 本轮先冻结的决定
1. 内容类型采用固定枚举，不开放自定义。
2. 用户画像以长期积累为主，不做重问卷。
3. 每次任务规划最多补充提问一次。
4. AI 负责规划、排程、提醒和是否使用 MCP。
5. 提醒走系统本地通知，不走聊天框。
6. 远程 HTTP 是首个 MCP 接入方式。
7. 第一版用 4 套 Prompt，而不是统一大 Prompt。
8. 第一版采用结构化卡片展示计划结果。
