# Phase 2B: Settings & Config UI

## Goal
实现完整的设置页面，包括 LLM 模型配置、MCP 服务配置、心跳频率设置、静默时段设置、隐私控制、数据管理，以及连接测试功能。

## Requirements
- 完善 SettingsScreen：展示所有设置分区（AI模型、MCP服务、心跳通知、隐私、数据管理）
- 实现 ModelConfigScreen：新增/编辑 LLM 配置，含预设模板、表单校验、连接测试
- 实现 McpConfigScreen：新增/编辑 MCP 服务配置，含认证方式选择、工具白名单、连接测试
- 实现 SettingsViewModel：管理所有设置状态，联动 DataStore 和 Repository
- 实现 ModelConfigViewModel：表单状态、校验、测试连接、保存
- 实现 McpConfigViewModel：表单状态、校验、测试连接、保存
- 实现 TestLlmConnectionUseCase：发送测试请求验证 LLM 配置
- 实现 TestMcpConnectionUseCase：调用 tools/list 验证 MCP 配置
- 心跳频率切换（LOW/MEDIUM/HIGH）
- 静默时段设置（开始/结束时间）
- 联网开关
- 数据清理功能（清除记忆/通知/全部数据）

## Acceptance Criteria
- [ ] Settings 页面展示所有分区
- [ ] 可新增、编辑、删除 LLM 配置
- [ ] LLM 连接测试可返回成功/失败
- [ ] 可新增、编辑、删除、启用/禁用 MCP 配置
- [ ] MCP 连接测试可返回工具列表
- [ ] API Key 以密码模式显示
- [ ] 心跳频率可切换
- [ ] 静默时段可设置
- [ ] 联网开关可控制
- [ ] 数据清理有确认弹窗
- [ ] 表单校验阻止无效配置保存
- [ ] 项目可编译通过

## Technical Notes
- 参考 docs/plans/08-settings-config.md 获取详细设计
- 依赖 Phase 2A 的 Entity/DAO/Repository（LlmConfigEntity, McpServerEntity, ConfigRepository）
- 连接测试使用 OkHttp 直接发请求（不依赖完整 LlmClient）
- 预设模板：OpenAI, DeepSeek, 自定义
- MCP 认证方式：NONE / BEARER / HEADER
