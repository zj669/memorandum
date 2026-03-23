# 规划失败处理 - 失败状态 + 重试/删除按钮

## Goal
当 AI 规划失败时，Entry 应自动变为"规划失败"状态，并在 UI 上提供重试和删除按钮，而不是永远显示"规划中"。

## Requirements

### Backend: 失败状态处理
- `PlanningOrchestrator` 中规划失败时，将 Entry 的 `planningState` 设为 `FAILED`（确认 `PlanningState` 枚举中已有 FAILED）
- 确保所有异常路径（LLM 调用失败、JSON 解析失败、DB 保存失败）都能正确将状态设为 FAILED
- 检查 `processClarifierOutput()` 和 `processPlannerOutput()` 中的 catch 块

### Frontend: TodayScreen 失败展示
- 在 TodayScreen 的"规划中"区域，对 `FAILED` 状态的 Entry 显示不同的 UI：
  - 红色/警告色调的卡片
  - 显示"规划失败"文字
  - **重试按钮**: 调用 ViewModel 重新触发 `startPlanning(entryId)`
  - **删除按钮**: 弹出确认对话框，确认后删除该 Entry
- `TodayViewModel` 需要添加 `retryPlanning(entryId)` 和 `deleteEntry(entryId)` 方法

### Edge Cases
- 重试时将状态重置为初始规划状态
- 删除时同时清理关联的 Task/PlanStep/ScheduleBlock（如果有部分数据）

## Key Files
- `app/src/main/java/com/memorandum/ai/orchestrator/PlanningOrchestrator.kt` — 确认失败路径设置 FAILED
- `app/src/main/java/com/memorandum/data/local/room/enums/PlanningState.kt` — 确认 FAILED 枚举值存在
- `app/src/main/java/com/memorandum/ui/today/TodayScreen.kt` — 失败卡片 UI
- `app/src/main/java/com/memorandum/ui/today/TodayViewModel.kt` — retry/delete 方法
- `app/src/main/java/com/memorandum/data/repository/EntryRepository.kt` — 可能需要 delete 方法
- `app/src/main/java/com/memorandum/ui/common/ConfirmDialog.kt` — 删除确认对话框

## Acceptance Criteria
- [ ] 规划失败时 Entry planningState 自动变为 FAILED
- [ ] TodayScreen 对 FAILED 状态显示失败卡片（区别于规划中）
- [ ] 重试按钮可用，点击后重新开始规划
- [ ] 删除按钮可用，点击后弹出确认，确认后删除 Entry
- [ ] Build passes
