# Phase 2A: Data Layer - Room + DataStore + Repository

## Goal
实现完整的本地数据持久化层，包括 Room 数据库 12 张表、所有 DAO、TypeConverter、枚举类型、DataStore 配置存储、Repository 接口与实现、CryptoHelper 加密工具。

## Requirements
- 定义所有枚举类型（EntryType, TaskStatus, PlanningState, MemoryType, NotificationType, NotificationActionType, StepStatus, PrepStatus, ScheduleSource）
- 实现 12 个 Entity（entries, tasks, schedule_blocks, plan_steps, prep_items, memories, user_profile, task_events, heartbeat_logs, notifications, llm_configs, mcp_servers）
- 实现 12 个 DAO，包含所有查询方法
- 实现 TypeConverter（List<String> <-> JSON）
- 实现 MemorandumDatabase（@Database 定义，所有 Entity 注册）
- 实现 AppPreferencesDataStore（心跳频率、静默时段、联网开关等）
- 实现 5 个 Repository 接口 + 实现（Entry, Task, Memory, Notification, Config）
- 实现 CryptoHelper（Android Keystore AES-GCM 加密 API Key）
- 配置 DatabaseModule Hilt 提供 Database + 所有 DAO
- 配置 DataStoreModule Hilt 提供 DataStore

## Acceptance Criteria
- [ ] 所有枚举类型定义在 data/local/room/enums/
- [ ] 12 个 Entity 定义正确，含 ForeignKey、Index
- [ ] 12 个 DAO 含完整 CRUD + 查询方法
- [ ] TypeConverter 正确处理 List<String> <-> JSON
- [ ] MemorandumDatabase 注册所有 Entity，exportSchema=true
- [ ] AppPreferencesDataStore 可读写所有配置项
- [ ] 5 个 Repository 接口 + 实现完整
- [ ] CryptoHelper 加解密正确
- [ ] DatabaseModule 和 DataStoreModule 正确提供依赖
- [ ] 项目可编译通过

## Technical Notes
- 参考 docs/plans/02-data-layer.md 获取详细设计
- 使用 kotlinx.serialization 做 JSON 转换
- Entity 中 Boolean 映射为 Room INTEGER (0/1)
- 所有 ID 为 UUID String
- 时间戳为 Long (epoch millis)
- ForeignKey 使用 CASCADE 删除
