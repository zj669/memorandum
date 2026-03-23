# 规划链路修复 + 清理测试代码

## Goal
1. Wire AlarmScheduler into PlanningOrchestrator.savePlanToDatabase() so AI-planned schedule blocks automatically register system alarms
2. Clean up DebugAlarmActivity and its manifest entry

## Requirements

### Part 1: Alarm Wiring
- Add `AlarmScheduler` as constructor parameter to `PlanningOrchestrator` (Hilt auto-resolves, no module change needed)
- After `taskRepository.saveFromPlan()` in `savePlanToDatabase()`, iterate schedule blocks and call `alarmScheduler.scheduleTaskAlarm()` for each future block
- Parse `blockDate` (e.g. "2026-03-23") + `startTime` (e.g. "09:00") into epoch millis using `java.time`
- Handle multiple blocks per task: use `block.id.hashCode()` as request code instead of `taskId.hashCode()` — need to add a `requestCode` parameter or overload to `AlarmScheduler.scheduleTaskAlarm()`
- Per-block try/catch so malformed LLM dates don't crash the save flow
- Only schedule blocks in the future (triggerAtMillis > now)
- Notification title: "即将开始: ${task.title}", body: block.reason

### Part 2: Cleanup
- Remove `DebugAlarmActivity.kt` from `app/src/main/java/com/memorandum/scheduler/`
- Remove its `<activity>` entry from `AndroidManifest.xml`

## Key Files
- `app/src/main/java/com/memorandum/ai/orchestrator/PlanningOrchestrator.kt` — main change, line ~286
- `app/src/main/java/com/memorandum/scheduler/AlarmScheduler.kt` — add overload or requestCode param
- `app/src/main/java/com/memorandum/data/local/room/entity/ScheduleBlockEntity.kt` — reference for fields
- `app/src/main/java/com/memorandum/scheduler/DebugAlarmActivity.kt` — DELETE
- `app/src/main/AndroidManifest.xml` — remove DebugAlarmActivity entry

## Acceptance Criteria
- [ ] PlanningOrchestrator has AlarmScheduler injected
- [ ] savePlanToDatabase() schedules alarms for each future schedule block
- [ ] Multiple blocks per task each get their own alarm (unique request codes)
- [ ] Malformed dates from LLM are caught and logged, not crashed
- [ ] DebugAlarmActivity.kt deleted
- [ ] DebugAlarmActivity removed from AndroidManifest.xml
- [ ] Build passes
