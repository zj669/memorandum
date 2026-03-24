# Task: memory-heartbeat-reminder-fixes

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

## Acceptance Criteria
- [ ] 将任务状态改为 `DONE` 或执行“快速完成”通知动作后，会写入正确 `task_events`，并可触发长期记忆更新流程。
- [ ] 接受计划（如接受 schedule block/accepted plan）会记录可被 `CollectEvidenceUseCase` 识别的 `ACCEPTED_PLAN` 事件，而不是仅更新本地 UI/数据库字段。
- [ ] 即时触发事件（至少 `DONE`、`ACCEPTED_PLAN`）不会再被 `MemoryOrchestrator` 内部阈值二次拦截，记忆更新成功时会产生可在 Memory 页面观察到的数据变化。
- [ ] 心跳在 AI 输出 `should_notify=true` 且未被静默/冷却/去重拦截时，会真正显示系统通知，而不仅是写入 `notifications` 表或 `heartbeat_logs`。
- [ ] 通知发送链路在缺少通知权限时有明确降级日志或状态反馈，不会静默失败且无可见提示。
- [ ] 设置页能够展示通知权限与精确闹钟权限状态，并提供用户可执行的授权引导入口。
- [ ] 到时提醒与稍后提醒在拥有权限时可正常注册 Alarm，并在设备重启后恢复未来仍应触发的提醒。
- [ ] `BootReceiver` 重启恢复逻辑同时覆盖 heartbeat 调度与未来提醒恢复，而非只恢复心跳。
- [ ] 通知点击后可导航到正确页面（Today 或对应 TaskDetail），不出现 extras 未消费导致的“点了无落点”问题。
- [ ] 项目相关模块可通过编译与必要检查，且修复范围内不存在明显的前后端/跨层协议不一致。

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
