# Journal - zj669 (Part 1)

> AI development session journal
> Started: 2026-03-22

---

## 2026-03-24 - memory-heartbeat-reminder-fixes-new handoff
- 已完成：通过 adb 真人操作验证长期记忆关键事件链路，`ACCEPTED_PLAN`、`STATUS_CHANGE`、`DONE` 均已正确写入 `task_events`。
- 已完成：修复 `MemoryOrchestrator` 的 `MemoryOutput` 解析失败问题。实现了 `extractJsonObject()` 清洗 fallback，并增强 parse failure 日志；`MemoryPrompt` 也补充了“只输出 JSON 对象”的约束。
- 已完成：编译安装成功，回归后日志显示 `Memory update completed: added=3, updated=2, downgraded=0`；数据库确认新增 3 条记忆、更新 2 条记忆；Memory 页面 UI 已同步出现新记忆和更新后的画像。
- 当前未完成：系统通知真实触达闭环仍未验证完成。已确认 notification channels 存在、历史 posted 统计存在，但当轮没有抓到活跃的 Memorandum 通知，也没有完成“心跳 should_notify=true 时系统栏真实出现通知”的实证。
- 当前未完成：通知权限 / 精确闹钟权限的设置页展示与引导、缺权限时的用户可见反馈，仍需继续核实。
- 当前未完成：到时提醒 / snooze / BootReceiver 重启恢复未来 Alarm 的链路仍待真人验证。
- 当前未完成：通知点击后的导航落点（Today / TaskDetail）与 extras 消费仍待闭环。
- 架构后续：仅靠提示词要求 JSON 不够可靠。已通过 Tavily 调研，下一步建议将 Planning / Heartbeat / Memory 三条链逐步升级到 provider 原生 structured outputs 或强制 tool calling，当前 `extractJsonObject()` 仅保留为 fallback。
- 下一会话建议顺序：1）系统通知真实触达；2）权限引导与缺权限降级；3）到时提醒与重启恢复；4）通知点击落点；5）统一结构化输出层设计。
- 新增阻塞项：`BootReceiver` 在 manifest 中对 `BOOT_COMPLETED` 使用 `android:exported=false`，高风险导致开机广播收不到，重启恢复链路不可信。
- 新增阻塞项：通知权限目前只有设置页跳转，没有 Android 13+ `POST_NOTIFICATIONS` runtime request 主路径，设置页“去授权”与实际行为不一致。
- 新增阻塞项：`RecordTaskEventUseCase` 用 detached `CoroutineScope` 异步触发记忆更新，即时事件 `DONE` / `ACCEPTED_PLAN` 存在沉淀延迟或被进程打断的稳定性风险。
