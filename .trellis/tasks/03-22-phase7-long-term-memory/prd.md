# Phase 7: Long-term Memory System

## Goal
实现 AI 驱动的长期记忆系统，包括记忆触发机制、行为证据收集、记忆应用、用户画像聚合、记忆衰减和去重、以及记忆注入规划的完整流程。

## Requirements

### 记忆触发 (domain/usecase/memory/)
- TriggerMemoryUpdateUseCase：判断是否触发记忆更新
  - 即时触发：任务完成(DONE)、计划被接受(ACCEPTED_PLAN)
  - 累积触发：>=5个事件 且 距上次更新>=6小时
- RecordTaskEventUseCase：记录任务事件并检查触发

### 证据收集
- CollectEvidenceUseCase：收集最近行为证据
  - 已完成任务、接受/拒绝的计划、通知响应、排程遵守情况、状态变更
  - 输出 EvidenceSummary，可转为 Prompt 文本（<=2000字符）

### 记忆应用
- ApplyMemoryOutputUseCase：将 AI 输出应用到数据库
  - 新增记忆（confidence 裁剪到 0-1）
  - 更新记忆（合并 source_refs）
  - 降权记忆
  - 清理极低置信度记忆（<0.1）
  - 触发画像重新聚合

### 用户画像聚合
- AggregateProfileUseCase：从高置信度记忆聚合画像
  - 提取各类型记忆的 top 3
  - 输出 UserProfileJson 结构
  - 写入 user_profile 表

### 记忆生命周期
- MemoryDecayManager：定期衰减
  - 90天未使用：confidence * 0.9
  - 180天未使用：confidence * 0.7
  - <0.1 自动删除
- MemoryDeduplicator：新增前检查重复（同类型+主题相似度>0.8）

### 记忆注入
- MemoryInjector：为 Planner Prompt 准备记忆上下文
  - 按相关性排序，取 top 10
  - 标记使用时间
  - 输出 MemoryContext（<=1500字符）

### 页面数据支持
- MemoryDisplayUseCase：为 Memory 页面提供数据
  - 按类型筛选、删除记忆后重新聚合

## Acceptance Criteria
- [ ] 任务完成时自动触发记忆更新
- [ ] 累积事件达阈值时触发记忆更新
- [ ] AI 输出的新增/更新/降权正确应用
- [ ] 用户画像聚合正确
- [ ] 记忆注入按相关性排序
- [ ] 记忆衰减机制正常
- [ ] 极低置信度记忆被清理
- [ ] 重复记忆被合并
- [ ] Memory 页面 ViewModel 接入真实数据
- [ ] 项目可编译通过

## Technical Notes
- 参考 docs/plans/07-long-term-memory.md
- 使用已有的 MemoryOrchestrator（Phase 4）
- 使用已有的 MemoryRepository、MemoryDao、UserProfileDao（Phase 2A）
- domain/usecase/memory/ 目录下实现
- 删除 domain/usecase/ 下的 .gitkeep
