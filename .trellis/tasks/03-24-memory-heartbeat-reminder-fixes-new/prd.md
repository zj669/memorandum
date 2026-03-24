# Task: memory-heartbeat-reminder-fixes-new

## Overview
修复长期记忆、心跳通知与到时提醒三条彼此关联但已部分断裂的产品链路，确保关键任务事件能稳定沉淀为长期记忆、心跳 AI 决策能真正触发系统通知送达用户、以及到时提醒在权限缺失和设备重启场景下仍具备完整可恢复性。

## Requirements
- 修复长期记忆自动沉淀链路，确保任务完成、计划接受以及通知动作等关键事件能够正确记录并按既定规则触发记忆更新。
- 对齐 `RecordTaskEventUseCase`、`TriggerMemoryUpdateUseCase` 与 `MemoryOrchestrator` 的职责边界，避免存在“事件已记录但未触发更新”或“即时触发规则与编排器内部阈值互相抵消”的问题。
- 补齐任务详情、通知动作或其他关键入口中的事件类型写入，使 `DONE`、`ACCEPTED_PLAN`、`SNOOZE` 等行为能够进入 `task_events` 并参与记忆证据收集。
- 确保记忆更新完成后，Memory 页面可观察到真实记忆与画像数据，不再因链路失效而长期为空。
- 补齐心跳提醒的系统通知触达链路：当 `HeartbeatOrchestrator` 决策 `should_notify=true` 时，除写入通知记录外，还必须真正调用通知发送能力让用户收到提醒。
- 保持心跳现有客户端保护链有效，包括静默时段、冷却、去重及任务状态检查，不因补发通知而破坏原有约束。
- 补齐到时提醒与心跳提醒所需的权限引导，包括至少覆盖通知权限与精确闹钟权限的状态判断、设置页展示和授权引导入口。
- 补齐设备重启后的提醒恢复机制，不仅恢复 heartbeat WorkManager 调度，还要恢复未来时间块/稍后提醒所需的 Alarm。
- 校验通知点击与动作链路，确保通知打开落点、稍后提醒、快速完成与相关事件记录形成闭环。
- 将 Planning / Heartbeat / Memory 三条 AI 输出链从“仅靠提示词约束 JSON 文本”升级为优先使用 tool/function calling 的结构化输出方案；现有 `stripMarkdownCodeBlock()`、`extractJsonObject()` 一类文本清洗仅作为 fallback，不再作为主路径。
- 为结构化输出链路补齐统一的 schema contract、tool 参数校验、错误分类与受控重试策略，避免不同 orchestrator 各自维护脆弱的字符串解析逻辑。

## Acceptance Criteria
- [x] 将任务状态改为 `DONE` 或执行“快速完成”通知动作后，会写入正确 `task_events`，并可触发长期记忆更新流程。
- [x] 接受计划（如接受 schedule block/accepted plan）会记录可被 `CollectEvidenceUseCase` 识别的 `ACCEPTED_PLAN` 事件，而不是仅更新本地 UI/数据库字段。
- [x] 即时触发事件（至少 `DONE`、`ACCEPTED_PLAN`）不会再被 `MemoryOrchestrator` 内部阈值二次拦截，记忆更新成功时会产生可在 Memory 页面观察到的数据变化。
- [ ] 将 Planning / Heartbeat / Memory 的 AI 输出主路径升级为 tool/function calling，并以 schema 校验后的结构化参数驱动业务逻辑，而不是继续依赖“输出一段 JSON 文本再清洗解析”。
- [ ] 若 provider / 兼容层暂不支持目标 tool 调用能力，系统需显式记录能力缺口并走受控 fallback；fallback 失败时要有明确日志与失败状态，不能静默吞掉。
- [x] 设置页能够展示通知权限与精确闹钟权限状态，并提供用户可执行的授权引导入口。
- [ ] 到时提醒与稍后提醒在拥有权限时可正常注册 Alarm，并在设备重启后恢复未来仍应触发的提醒。
- [x] `BootReceiver` 重启恢复逻辑同时覆盖 heartbeat 调度与未来提醒恢复，而非只恢复心跳。
- [ ] 通知点击后可导航到正确页面（Today 或对应 TaskDetail），不出现 extras 未消费导致的“点了无落点”问题。
- [x] 项目相关模块可通过编译与必要检查，且修复范围内不存在明显的前后端/跨层协议不一致。

## Session Handoff (2026-03-24)
- 已完成：长期记忆自动沉淀链路已真人验证打通。通过 adb 在真机模拟流中触发 `ACCEPTED_PLAN`、`PLANNED -> DOING`、`DOING -> DONE`，确认 `task_events` 正确入库。
- 已完成：`MemoryOrchestrator` 解析链已修复。当前不再只依赖提示词输出纯 JSON，而是增加了 `extractJsonObject()` 作为 fallback 清洗，并增强了 parse failure 日志；同时 `MemoryPrompt` 增加“只输出 JSON 对象”的约束。编译、安装、回归均通过。
- 已完成：修复后再次真人回归，日志确认 `Memory update completed: added=3, updated=2, downgraded=0`，数据库中新增 3 条记忆、更新 2 条记忆，`user_profile.version` 升到 4，Memory 页 UI 已显示新内容。
- 未完成：系统通知真实触达尚未闭环验证。此前只确认过 `com.memorandum` 的 notification channels 已注册、`numPostedByApp=3`，但当轮未抓到活跃的 Memorandum 通知记录，也未完成“心跳 should_notify=true -> 系统栏出现通知”的真人验证。
- 未完成：通知权限与精确闹钟权限的设置页展示/引导仍需核实与补齐，尤其要验证缺权限时的用户可见反馈，而不只是日志降级。
- 未完成：到时提醒 / snooze / 设备重启恢复链路仍需真人验证，尤其是 Alarm 注册是否成功、BootReceiver 是否恢复未来提醒而不仅是 heartbeat。
- 未完成：通知点击落点（Today / TaskDetail）与 extras 消费链路仍未做最终闭环。
- 风险提醒：当前记忆链虽然已通过，但主方案仍是“prompt 约束 + 文本提取 fallback + schema validate”。下一步不应继续把这套文本解析链当成正式方案，而应切换到更规范的 tool/function calling 主路径，由模型提交结构化参数，再做本地 schema / business validation；文本提取仅保留兜底。
- 已新增优化方向：后续需为 Planning / Heartbeat / Memory 统一抽象结构化输出层，优先采用 tool/function calling；若底层 OpenAI-compatible provider 不完整支持，再在能力探测后降级到受控 fallback，并记录 provider capability gap，避免 silently degrade。
- 下一个会话优先级建议：1）系统通知真实触达验证；2）权限引导与缺权限降级验证；3）到时提醒/重启恢复验证；4）通知点击落点验证；5）评估将 Planning / Heartbeat / Memory 三条链统一升级为结构化输出层。
- 新增阻塞项：`BootReceiver` 在 `AndroidManifest.xml` 中声明了 `BOOT_COMPLETED` intent-filter，但当前 `android:exported="false"`，高风险导致系统开机广播无法送达，进而使 heartbeat / future alarms 的重建链路在真机重启后失效。
- 新增阻塞项：通知权限当前只有设置页跳转入口，没有 Android 13+ `POST_NOTIFICATIONS` runtime request 主路径；`SettingsScreen` 文案是“去授权”，但实际行为只是打开系统设置，用户授权闭环与真人测试路径都不完整。
- 新增阻塞项：`RecordTaskEventUseCase` 通过 detached `CoroutineScope(SupervisorJob() + Dispatchers.IO)` 异步触发 `TriggerMemoryUpdateUseCase`，调用方不等待结果，导致 `DONE` / `ACCEPTED_PLAN` 等即时事件在真人操作后存在记忆沉淀延迟或被进程中断打断的稳定性风险。

## Session Handoff (2026-03-25)
- 已完成：`BootReceiver` manifest blocker 已修复并真人验证。`AndroidManifest.xml` 中 `.scheduler.BootReceiver` 已改为 `android:exported="true"`；通过真实重启模拟器而非伪造 shell 广播验证，logcat 命中 `Boot completed, restoring heartbeat schedule`、`Restored 0 future schedule alarms`、`Restored 0 snoozed alarms`、`Heartbeat and alarms restored: frequency=HIGH`，确认恢复链不再只停留在注册态。
- 已完成：设置页通知权限引导已补齐并真人验证。通过 adb 撤销 `POST_NOTIFICATIONS` 后进入 Settings，UI 显示“未授权”与“立即授权”；点击后系统进入 `GrantPermissionsActivity` 授权弹窗，允许后 `dumpsys package com.memorandum` 显示 `android.permission.POST_NOTIFICATIONS: granted=true`。
- 已完成：`RecordTaskEventUseCase` 的 detached coroutine blocker 已修复。当前改为在既有 `suspend` 调用链中直接执行 `triggerMemoryUpdateUseCase.checkAndTrigger(eventType)`，并补充带 `taskId` / `eventType` 的失败日志，不再依赖游离 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`。
- 已完成：长期记忆即时触发链再次真人验证打通。TaskDetail UI 中对样本任务执行“接受时间安排”与“更多操作 -> 已完成”后，logcat 分别命中 `ACCEPTED_PLAN` / `DONE` -> `TriggerMemoryUpdate` -> `MemoryOrchestrator: Memory update triggered: force=true`；其中 `DONE` 路径进一步命中 `Memory update completed: added=0, updated=0, downgraded=0`。
- 已完成：通过 WAL 级数据库取证确认事件与状态真正落库，而不是只存在于 UI / 内存态。取出 `memorandum.db` + `memorandum.db-wal` + `memorandum.db-shm` 后，SQLite 中可见 `tasks.status='DONE'`，且 `task_events` 包含 `ACCEPTED_PLAN`、`STATUS_CHANGE`、`DONE` 事件；此前冷库快照未见最新状态，根因是只拷主库文件未并入 WAL。
- 已完成：本轮代码已重新 `:app:compileDebugKotlin`、`:app:installDebug`、`:app:assembleDebug` 验证通过，debug APK 可用。
- 未完成：系统通知真实触达仍未闭环到“由应用自身触发一条真实 Memorandum 通知并在系统通知栏执行 action”的粒度；当前只完成了通知权限主路径与通知 channel 存在性验证，尚未抓到应用侧主动发出的活跃 Memorandum 通知实例。
- 未完成：到时提醒 / snooze 的“已注册 Alarm -> 真实到时弹出 -> 重启后仍恢复”尚未以非零 future alarm 样本完成端到端验证；本次重启验证确认了恢复逻辑执行，但恢复计数为 0。
- 未完成：通知点击落点（Today / TaskDetail）与 extras 消费链路未做最终真人闭环，虽然代码阅读显示 `MainActivity` + `AppNavGraph` 已消费导航 extras，但仍缺系统通知点击证据。
- 下一个会话优先级建议：1）构造一个非零 future alarm 样本并验证真实通知触达；2）验证通知 action / 点击落点；3）继续推进 structured output 主路径替换文本 JSON fallback。
## Structured Output Optimization Notes (2026-03-24)
- 调研结论：仅靠 prompt 要求模型“输出 JSON”在工程上不可靠，常见失败模式包括 markdown code fence、解释性前后缀、字段漂移、半截 JSON 与 schema 不匹配；当前项目中的 `stripMarkdownCodeBlock()`、`extractJsonObject()` 只能补救，不能作为长期主方案。
- 方案决策：后续结构化输出优化统一采用 **tool/function calling** 作为主路线，而不是继续押注纯文本 JSON。模型侧应通过明确的 tool schema 提交 `PlannerOutput`、`ClarifierOutput`、`HeartbeatOutput`、`MemoryOutput` 对应参数，再由本地执行层做 schema 校验与业务校验。
- 落地原则：
  - 为 Planning / Heartbeat / Memory 分别定义稳定 tool 名称与参数 schema，避免每个 orchestrator 各自手写 prompt 解析协议。
  - `LlmClient` / OpenAI-compatible client 需要补充 capability 探测：是否支持 tools、tool_choice、严格参数 schema；能力不足时显式降级，不允许“假装成功但回到脆弱文本模式”而无记录。
  - 保留现有文本 JSON 解析链仅作为 fallback，并要求 fallback 命中时打日志、记 telemetry，方便后续彻底淘汰。
  - `SchemaValidator` 继续保留，但职责从“修补自由文本输出”转为“校验 tool 参数与业务约束”。
- 推荐迁移顺序：先改 `HeartbeatOrchestrator` 与 `MemoryOrchestrator`，再改 `PlanningOrchestrator` / clarifier / MCP 二轮规划链，因为后者输出结构更复杂、联动更多。

## Technical Notes
- 现有长期记忆规格要求 `DONE` 与 `ACCEPTED_PLAN` 为即时触发事件，但当前 `MemoryOrchestrator.updateMemories()` 仍通过 `shouldTrigger()` 要求最近事件数达到阈值，可能导致即时触发被二次拦截。
- `TaskDetailViewModel` 当前状态变更记录的是 `STATUS_CHANGE`，`onScheduleAccepted()` 仅更新 `schedule_block.accepted`，与长期记忆规格中要求的 `DONE` / `ACCEPTED_PLAN` 证据类型并不一致。
- `NotificationActionReceiver.handleMarkDone()` 直接通过 `taskRepository.recordEvent()` 写事件，该路径会绕过 `RecordTaskEventUseCase` 的异步触发检查，可能造成“事件入库但记忆不更新”。
- `HeartbeatOrchestrator` 当前已完成 AI 决策、去重保护与 `notifications` 表写入，但没有明显调用 `NotificationHelper.send()`，因此可能只写库不触达系统通知。
- `BootReceiver` 目前仅恢复 heartbeat 频率调度，尚未扫描未来 schedule block 或 snooze 记录并重新注册 Alarm。
- `AlarmScheduler` 已声明 exact alarm 权限检查，但缺少 UI 层权限状态展示与引导；`SettingsScreen` 已有“心跳与通知”区块，适合作为权限补齐入口。
- `MainActivity` 目前未看到消费通知导航 extras 的逻辑，通知点击落点需要与导航层一并验证。

## Out of Scope
- 不重做长期记忆算法本身（如去重、衰减、画像聚合策略）的产品规则，只修复触发与落库链路。
- 不重新设计心跳 AI prompt、通知文案策略或 MCP 选择策略，除非为修复触达链路所必需。
- 不引入远程推送、云端消息或新的后台常驻服务；本任务聚焦于本地通知、WorkManager、AlarmManager 与现有编排。
- 不扩展全新的设置模块或大型信息架构调整，仅在现有设置/提醒链路上补齐必要入口与恢复逻辑。
