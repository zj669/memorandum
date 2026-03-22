# Memorandum - 模块实现计划索引

## 模块清单

| 编号 | 模块 | 文件 | 核心职责 |
|------|------|------|----------|
| 01 | 项目骨架与基础设施 | [01-project-skeleton.md](./01-project-skeleton.md) | Android 项目、Hilt DI、Compose Navigation、分层架构 |
| 02 | 数据层 | [02-data-layer.md](./02-data-layer.md) | Room 12 张表、DAO、DataStore、Repository、加密 |
| 03 | UI 层 | [03-ui-layer.md](./03-ui-layer.md) | 7 个页面、公共组件、ViewModel、UiState |
| 04 | AI 编排层 | [04-ai-orchestration.md](./04-ai-orchestration.md) | 4 套 Prompt、LLM 客户端、Schema 校验、编排器 |
| 05 | MCP 工具调用 | [05-mcp-integration.md](./05-mcp-integration.md) | HTTP MCP 客户端、结果裁剪、缓存、隐私保护 |
| 06 | 心跳与通知 | [06-heartbeat-notification.md](./06-heartbeat-notification.md) | WorkManager、AlarmManager、通知系统、硬保护 |
| 07 | 长期记忆 | [07-long-term-memory.md](./07-long-term-memory.md) | 记忆触发、证据收集、画像聚合、衰减管理 |
| 08 | 设置与配置 | [08-settings-config.md](./08-settings-config.md) | LLM/MCP 配置、连接测试、权限引导、首次引导 |

## 依赖关系图

```
01 项目骨架
 └──> 02 数据层
       ├──> 03 UI 层
       ├──> 04 AI 编排层
       │     ├──> 05 MCP 工具调用
       │     ├──> 06 心跳与通知
       │     └──> 07 长期记忆
       └──> 08 设置与配置
```

## 推荐开发顺序

```
Phase 1:  01 项目骨架
Phase 2:  02 数据层 + 08 设置与配置（可并行）
Phase 3:  03 UI 层（基础页面骨架）
Phase 4:  04 AI 编排层
Phase 5:  05 MCP 工具调用（与 Phase 4 后半段并行）
Phase 6:  06 心跳与通知
Phase 7:  07 长期记忆
Phase 8:  全链路集成测试 + UI 打磨
```

## 关键里程碑

| 里程碑 | 完成模块 | 可验证能力 |
|--------|----------|------------|
| M1 - 可运行骨架 | 01 | App 启动，Tab 切换正常 |
| M2 - 数据可存取 | 01 + 02 | 条目创建、任务保存、配置读写 |
| M3 - 基础可用 | 01~03 + 08 | 完整 UI 流程，可配置 LLM/MCP |
| M4 - AI 规划可用 | 01~05 | 创建条目 → AI 规划 → 展示结果 |
| M5 - 通知可用 | 01~06 | 心跳巡检 → 本地通知 → 点击跳转 |
| M6 - MVP 完整 | 01~08 | 长期记忆积累，完整闭环 |
