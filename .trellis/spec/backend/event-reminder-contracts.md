# Event, Reminder, and Recovery Contracts

> Executable contracts for task-event-driven memory updates, notification delivery, and reboot alarm recovery.

---

## Scenario: Immediate memory updates from task events

### 1. Scope / Trigger
- Trigger: task completion, accepted plan, and notification action flows now cross `ui/`, `scheduler/`, `domain/usecase/memory/`, `ai/orchestrator/`, and Room storage.
- This needs code-spec depth because a single missing event write or a second threshold gate can make the Memory page stay empty even though the user completed work.

### 2. Signatures
- `app/src/main/java/com/memorandum/domain/usecase/memory/RecordTaskEventUseCase.kt`
  - `suspend fun record(taskId: String, eventType: String, payload: String? = null)`
- `app/src/main/java/com/memorandum/domain/usecase/memory/TriggerMemoryUpdateUseCase.kt`
  - `suspend fun checkAndTrigger(triggerEvent: String): MemoryUpdateResult`
- `app/src/main/java/com/memorandum/ai/orchestrator/MemoryOrchestrator.kt`
  - `suspend fun updateMemories(force: Boolean = false): MemoryUpdateResult`
- Writers that must use the use case instead of direct repository event insertion:
  - `app/src/main/java/com/memorandum/ui/taskdetail/TaskDetailViewModel.kt`
  - `app/src/main/java/com/memorandum/scheduler/NotificationActionReceiver.kt`

### 3. Contracts
- `task_events.event_type` values that must be written for memory evidence:
  - `DONE`
  - `ACCEPTED_PLAN`
  - `SNOOZE`
  - `STATUS_CHANGE` only for non-terminal status transitions
- Immediate trigger contract:
  - `TriggerMemoryUpdateUseCase.checkAndTrigger("DONE" | "ACCEPTED_PLAN")` must call `memoryOrchestrator.updateMemories(force = true)`.
  - `MemoryOrchestrator.updateMemories()` must not apply a second minimum-event threshold gate internally.
- Payload contract:
  - `DONE` payload may contain previous status or notification record id.
  - `ACCEPTED_PLAN` payload should carry the accepted `schedule_block.id`.
  - `SNOOZE` payload should carry the source notification record id.
- UI observation contract:
  - `MemoryViewModel` reads `observeMemories()` plus `observeProfile()`; successful memory application must therefore surface as non-empty memory/profile state without extra refresh APIs.

### 4. Validation & Error Matrix
- Direct `taskRepository.recordEvent()` used for memory-relevant actions -> invalid, because insert succeeds but trigger check is bypassed.
- `DONE` or `ACCEPTED_PLAN` recorded as `STATUS_CHANGE` only -> invalid, because `CollectEvidenceUseCase` will not classify it as immediate evidence.
- `TriggerMemoryUpdateUseCase` immediate branch forgets `force = true` -> invalid, because orchestrator-level gating can suppress required updates.
- No recent events in `MemoryOrchestrator.updateMemories()` -> return `MemoryUpdateResult.Skipped`.
- LLM parse/schema failure in `MemoryOrchestrator` -> return `MemoryUpdateResult.Failed(...)`, do not write partial memories.

### 5. Good / Base / Bad Cases
- Good: task detail marks task `DONE` -> `RecordTaskEventUseCase.record(taskId, "DONE", previousStatus)` -> trigger use case forces memory update -> Memory page observes new memory/profile rows.
- Base: non-terminal status change -> record `STATUS_CHANGE` only -> contributes to accumulated evidence but does not force update.
- Bad: notification quick-complete updates task status and inserts `task_events` directly through repository -> event exists, memory update never starts.

### 6. Tests Required
- Unit: `TriggerMemoryUpdateUseCase`
  - assert `DONE` and `ACCEPTED_PLAN` call `updateMemories(force = true)`
  - assert accumulated branch still respects interval + threshold
- Unit: `TaskDetailViewModel`
  - assert `DONE` writes `DONE`, not `STATUS_CHANGE`
  - assert schedule acceptance writes `ACCEPTED_PLAN`
- Unit/Integration: notification action handling
  - assert quick-complete and snooze call `RecordTaskEventUseCase`
- Integration: memory pipeline
  - insert qualifying `task_events`, run trigger path, assert Memory/Profile tables change and `MemoryViewModel` observes non-empty output

### 7. Wrong vs Correct
#### Wrong
```kotlin
entryPoint.taskRepository().recordEvent(taskRef, "DONE", null)
```

#### Correct
```kotlin
entryPoint.recordTaskEventUseCase().record(taskRef, "DONE", notificationRecordId)
```

---

## Scenario: Notification delivery, navigation, and reboot recovery

### 1. Scope / Trigger
- Trigger: heartbeat decisions, exact alarms, notification actions, app navigation, and boot restore now share a contract across `ai/orchestrator/`, `scheduler/`, `ui/settings/`, `MainActivity`, and Room notifications/schedule blocks.
- This needs code-spec depth because writing a notification row is not sufficient; the user must either receive the system notification or see a clear permission-related degradation path.

### 2. Signatures
- `app/src/main/java/com/memorandum/ai/orchestrator/HeartbeatOrchestrator.kt`
  - `suspend fun executeHeartbeat(): HeartbeatResult`
- `app/src/main/java/com/memorandum/scheduler/NotificationHelper.kt`
  - `fun send(id: Int, notificationRecordId: String, title: String, body: String, channelId: String, taskRef: String?, actionType: NotificationActionType): Boolean`
- `app/src/main/java/com/memorandum/scheduler/AlarmScheduler.kt`
  - `fun scheduleTaskAlarm(alarmKey: String, taskId: String, taskTitle: String, triggerAtMillis: Long, notificationTitle: String, notificationBody: String)`
  - `fun cancelTaskAlarm(alarmKey: String)`
- `app/src/main/java/com/memorandum/scheduler/BootReceiver.kt`
  - restores heartbeat work + future schedule alarms + snoozed reminder alarms after `Intent.ACTION_BOOT_COMPLETED`
- `app/src/main/java/com/memorandum/MainActivity.kt`
  - consumes `navigate_to` and `task_id` extras into `NavigationRequest`

### 3. Contracts
- Delivery contract:
  - If heartbeat AI returns `should_notify=true` and dedup/quiet-hours checks pass, `HeartbeatOrchestrator` must both:
    1. save `NotificationEntity`
    2. call `NotificationHelper.send(...)`
- Permission degradation contract:
  - `NotificationHelper.send(...)` returns `false` when POST_NOTIFICATIONS is missing or app notifications are disabled.
  - Caller must log/store a visible failure reason instead of silently reporting success.
- PendingIntent extras contract:
  - notification click/action intents must preserve:
    - `notification_id: Int`
    - `notification_record_id: String`
    - `task_ref: String?`
    - `action_type: String` for open actions
- Navigation contract:
  - `navigate_to = "today"` -> open Today screen
  - `navigate_to = "task"` with `task_id` -> open TaskDetail screen
  - missing `task_id` -> degrade to Today
- Alarm key contract:
  - planner block reminders use `alarmKey = scheduleBlock.id`
  - snoozed reminders use `alarmKey = "snooze:$notificationRecordId"`
  - cancellation and boot restore must use the same key shape
- Settings contract:
  - Settings UI must expose current notification permission and exact-alarm permission state plus deep links to system settings.

### 4. Validation & Error Matrix
- `should_notify=true` but helper not called -> invalid, because DB row exists without user-visible notification.
- Helper returns `false` because notifications disabled -> caller records/logs permission failure and returns failed/skipped state, not silent success.
- Boot restore only reschedules heartbeat work -> invalid, because future block alarms and snoozed reminders are lost after reboot.
- Notification click missing `navigate_to`/`task_id` consumption in activity/nav -> invalid, because tapping notification has no landing destination.
- Reusing task id as every alarm key -> invalid, because multiple schedule blocks collide and later cancellation/restoration becomes incorrect.

### 5. Good / Base / Bad Cases
- Good: heartbeat chooses notify -> notification row saved -> helper sends system notification -> click opens Today or TaskDetail -> snooze reschedules exact alarm with `snooze:<notificationId>`.
- Base: notifications permission missing -> helper returns `false` -> heartbeat log records permission failure -> Settings page shows missing permission and offers system entry point.
- Bad: reboot occurs after user snoozes a reminder -> app restores only heartbeat periodic work -> snoozed reminder never fires again.

### 6. Tests Required
- Unit: `HeartbeatOrchestrator`
  - assert helper is called after notification save when protections pass
  - assert helper failure returns non-success result with permission-related reason
  - assert quiet-hours/dedup still short-circuit before save/send
- Unit: `NotificationHelper`
  - assert permission-disabled path returns `false`
  - assert click and action intents include `notification_record_id`, `task_ref`, and `action_type`
- Unit/Integration: `BootReceiver`
  - seed future `schedule_blocks` and snoozed `notifications`, simulate boot, assert both alarm families are rescheduled with stable keys
- Navigation test:
  - launch `MainActivity` with `navigate_to=task` + `task_id`, assert TaskDetail route is reached
  - launch with `navigate_to=today`, assert Today route is reached
- UI test: Settings screen
  - assert permission status rows render and action buttons are visible

### 7. Wrong vs Correct
#### Wrong
```kotlin
notificationRepository.save(entity)
return HeartbeatResult.Notified(notificationId)
```

#### Correct
```kotlin
notificationRepository.save(entity).getOrElse { error ->
    return HeartbeatResult.Failed("Save notification failed: ${error.message}")
}

val delivered = notificationHelper.send(
    id = notificationId.hashCode(),
    notificationRecordId = notificationId,
    title = notif.title,
    body = notif.body,
    channelId = notificationHelper.channelForType(notificationType),
    taskRef = taskRef,
    actionType = actionType,
)
if (!delivered) {
    return HeartbeatResult.Failed("Notification permission missing or notifications disabled")
}
```
