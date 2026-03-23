# Phase 6: Heartbeat & Notification System

## Goal
实现后台心跳机制和本地通知系统。WorkManager 周期性巡检，AlarmManager 精确时间提醒，客户端保留去重、静默时段和频率保护等硬约束。

## Requirements

### WorkManager 心跳
- HeartbeatWorker：CoroutineWorker，调用 HeartbeatOrchestrator
- HeartbeatScheduleManager：管理周期性心跳调度（LOW/MEDIUM/HIGH 频率）
- 支持暂停/恢复心跳

### AlarmManager 精确提醒
- AlarmScheduler：为时间块注册精确提醒（提前10分钟）
- AlarmReceiver：BroadcastReceiver 接收 Alarm 并发送通知
- 支持取消单个/全部任务的 Alarm
- Android 12+ SCHEDULE_EXACT_ALARM 权限处理

### 通知系统
- NotificationHelper：创建通知渠道（task/heartbeat/risk），构建并发送通知
- NotificationActionReceiver：处理通知动作（稍后提醒、标记完成）
- 通知点击 Deep Link 跳转到对应页面

### 客户端硬保护
- QuietHoursChecker：静默时段判断（DEADLINE_RISK 可突破）
- CooldownManager：任务冷却期管理（1-24小时）
- DeduplicationChecker：同任务同类型4小时内去重
- 保护链执行顺序：静默→冷却→去重→状态检查

### 权限管理
- PermissionManager：检查通知权限和精确闹钟权限
- BootReceiver：开机恢复 Alarm 和心跳

### 通知行为追踪
- NotificationBehaviorTracker：统计忽略率，连续忽略3次提高阈值

## Acceptance Criteria
- [ ] HeartbeatWorker 可按配置频率触发
- [ ] AlarmScheduler 可注册/取消精确提醒
- [ ] 通知可正常显示（标题、内容、动作按钮）
- [ ] 稍后提醒可延后1小时
- [ ] 标记完成可更新任务状态
- [ ] 静默时段内普通通知被拦截
- [ ] 冷却期内重复通知被拦截
- [ ] 去重逻辑正确
- [ ] BootReceiver 可恢复调度
- [ ] AndroidManifest 正确注册所有 Receiver
- [ ] 项目可编译通过

## Technical Notes
- 参考 docs/plans/06-heartbeat-notification.md
- 使用已有的 HeartbeatOrchestrator（Phase 4）
- 使用已有的 Repository 和 DataStore（Phase 2A）
- scheduler/ 目录下实现，删除 .gitkeep
