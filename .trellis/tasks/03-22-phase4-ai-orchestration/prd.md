# Phase 4: AI Orchestration Layer

## Goal
实现完整的 AI 调用编排逻辑，包括 4 套 Prompt 模板、LLM API 客户端、JSON 响应解析与校验、规划/心跳/记忆编排器、MCP 编排器。

## Requirements

### LLM 客户端 (data/remote/llm/)
- LlmClient 接口：chat(request) -> Result<LlmResponse>, testConnection() -> Result<Boolean>
- OpenAiCompatibleClient 实现：POST {baseUrl}/v1/chat/completions
- 支持 text + image 多模态输入（base64 编码）
- 强制 response_format: { type: "json_object" }
- 超时：连接 30s，读取 120s
- LlmRequest / LlmResponse / ImageInput / TokenUsage 数据类
- ImageProcessor：从 URI 读取图片、压缩、转 base64

### Prompt 模板 (ai/prompt/)
- PromptBuilder：组装 system prompt 通用部分、注入用户画像、注入记忆、注入时间上下文
- PlannerPrompt：规划 prompt，输入条目+画像+记忆+相似任务+MCP结果
- ClarifierPrompt：补充提问 prompt
- HeartbeatPrompt：心跳巡检 prompt
- MemoryPrompt：记忆沉淀 prompt

### 输出 Schema (ai/schema/)
- PlannerOutput (@Serializable)：needs_clarification, task_title, summary, steps, schedule_blocks, prep_items, risks, notification_candidates, should_use_mcp, mcp_queries
- ClarifierOutput：ask, question, reason
- HeartbeatOutput：should_notify, notification, reason, task_ref, cooldown_hours, should_use_mcp
- MemoryOutput：new_memories, updates, downgrades
- SchemaValidator：校验所有输出的合法性

### 编排器 (ai/orchestrator/)
- PlanningOrchestrator：完整规划流程（读取上下文→判断提问→执行规划→MCP→校验→保存）
- HeartbeatOrchestrator：心跳流程（汇总任务→调用AI→去重→通知决策）
- MemoryOrchestrator：记忆更新流程（收集证据→调用AI→应用变更→聚合画像）
- McpOrchestrator：MCP 调用编排（检查权限→选择服务→调用→裁剪结果）

### MCP 客户端 (data/remote/mcp/)
- McpClient 接口：listTools, callTool, testConnection
- HttpMcpClient 实现：JSON-RPC 2.0 over HTTP
- McpResultTrimmer：裁剪结果控制 token 量
- McpCache：内存 LRU 缓存，TTL 30分钟
- McpPrivacyFilter：隐私过滤

### 错误处理与重试
- retryWithBackoff 工具函数
- 网络错误重试2次，JSON解析重试1次，auth/schema不重试
- MCP 失败时降级为保底计划

## Acceptance Criteria
- [ ] LlmClient 可发送 chat completion 请求
- [ ] 4 套 Prompt 模板可正确组装
- [ ] 4 个 Output Schema 可正确解析 JSON
- [ ] SchemaValidator 覆盖所有约束条件
- [ ] PlanningOrchestrator 完整流程可走通
- [ ] HeartbeatOrchestrator 流程可走通
- [ ] MemoryOrchestrator 流程可走通
- [ ] McpClient 可调用远程 HTTP MCP
- [ ] MCP 失败时可降级
- [ ] 重试策略正确
- [ ] 项目可编译通过

## Technical Notes
- 参考 docs/plans/04-ai-orchestration.md 和 docs/plans/05-mcp-integration.md
- 使用 Phase 2A 已实现的 Entity/DAO/Repository
- 使用 OkHttp 发送 HTTP 请求
- 使用 kotlinx.serialization 解析 JSON
- Prompt 中文编写，输出要求 JSON
