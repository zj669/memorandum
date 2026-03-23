# 已完成任务归档展示

## Goal
已完成的任务（status=DONE）应与未完成的任务分开展示，而不是混在一起。

## Requirements

### TodayScreen 改造
- 当前 TodayScreen 的"推荐优先处理"区域混合展示所有任务
- 改为只展示未完成任务（status != DONE）
- 在页面底部或单独区域添加"已完成"折叠区域：
  - 默认折叠，显示已完成数量（如"已完成 3 项"）
  - 点击展开显示已完成任务列表
  - 已完成任务卡片样式淡化（降低不透明度或灰色调）

### TasksScreen 改造
- TasksScreen（任务列表页）同样需要分区展示
- 顶部展示活跃任务（TODO, IN_PROGRESS, PLANNED 等）
- 底部折叠区域展示已完成任务

### ViewModel 层
- `TodayViewModel`: 将任务流分为 activeTasks 和 doneTasks
- `TasksViewModel`: 同样分流

### 数据层
- 检查 `TaskDao` 是否有按状态查询的方法，没有则添加
- 可能需要 `observeByStatus(status)` 或 `observeExcludingStatus(status)` 方法

## Key Files
- `app/src/main/java/com/memorandum/ui/today/TodayScreen.kt` — 分区展示
- `app/src/main/java/com/memorandum/ui/today/TodayViewModel.kt` — 分流任务
- `app/src/main/java/com/memorandum/ui/tasks/TasksScreen.kt` — 分区展示
- `app/src/main/java/com/memorandum/ui/tasks/TasksViewModel.kt` — 分流任务
- `app/src/main/java/com/memorandum/data/local/room/dao/TaskDao.kt` — 按状态查询
- `app/src/main/java/com/memorandum/data/repository/TaskRepository.kt` — 可能需要新方法
- `app/src/main/java/com/memorandum/data/local/room/enums/TaskStatus.kt` — 参考状态枚举

## Acceptance Criteria
- [ ] TodayScreen 只在主区域展示未完成任务
- [ ] TodayScreen 底部有折叠的"已完成"区域
- [ ] TasksScreen 同样分区展示
- [ ] 已完成任务视觉上与活跃任务有区分（淡化/灰色）
- [ ] 折叠区域可展开/收起
- [ ] Build passes
