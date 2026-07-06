# 9 项 AI 深度融合实施计划

> **For Claude/Codex:** REQUIRED SUB-SKILL: Use `executing-plans` when executing this plan in a fresh session.

## Goal

把 9 个 AI 升级方向做成 Pythontopia 的有机能力，而不是独立 Demo：

1. 统一 Agent Runtime 控制面
2. 学习因果证据图 3.0
3. GraphRAG 答案引用与路线说明
4. Eval / Observability 驾驶舱
5. Learner Twin 长期学生记忆
6. 百晓工坊 Skill / Tool 包系统
7. ChatGPT Apps / MCP 外部入口
8. Realtime 语音 / 多模态导学
9. Web research / search-browser 双轨工具

完成标准：后端、RAG、graph 与前端同向闭环；详细测试覆盖；维护 `CHANGELOG.md`；按可验证批次提交。

## Architecture

- **证据优先**：资源阅读只记录事实，不直接提高掌握度或画像分数；微测、操作、正式提交、调试修复等可验证证据才改变 mastery/profile。
- **因果可追踪**：`resource_learning_check.id` 贯穿 `LearningEvent`、`learning_event_log`、`kc_mastery_event.reason` 和 Learning Evidence Graph，形成 `CHECK -> MASTERY_EVENT -> KC`。
- **引用可审计**：RAG 输出 `citations` 与 `route_reason`，前端展示课件页、KC、图扩展路线和无证据状态。
- **协议可组合**：保留现有 AG-UI / MCP / SSE 基座，在 runtime 层补 tool/schema/eval/trace 元数据。
- **前端即工作台**：百晓工坊采用 ChatGPT / Claude 类单会话工作台，左侧历史，中间对话流，右侧工件、引用、工具调用与评测证据。

## Implementation Batches

### Batch 1 - Evidence Spine

- TDD 修正 `ResourceCheckCompleted`，补 `checkId`。
- `LearningEventListener` 用 `resource_check:{checkId}` 写 mastery reason。
- `PassiveProfileRevisionListener` 停止用资源消费增加画像，改由资源微测/操作证据更新 `knowledge_base` / `practical_ability` / `error_pattern`。
- Learning Evidence Graph 补 `CHECK -> MASTERY_EVENT` 因果边。
- Memory stream 镜像 `checkId`，给 Learner Twin 和前端 trace 使用。

### Batch 2 - GraphRAG Citation Contract

- RAG response 增加 `citations`、`route_reason`。
- `graph_mode=force/auto/off` 都返回可解释路线。
- 无证据时强制空 citation，防止前端伪引用。
- 增加 golden/route/API contract 测试。

### Batch 3 - Runtime, Skills, Tools

- 抽象 agent runtime metadata：agent、tool、schema、guardrail、trace、eval hook。
- 百晓工坊 Skill 包支持 manifest、工具权限、证据页约束、输出 schema。
- 兼容 ChatGPT Apps / MCP 的工具描述和结构化结果。

### Batch 4 - Eval / Observability

- 对 RAG、资源生成、推荐、实时导学建立 eval case 与 trace schema。
- 后端暴露教师/演示可读的 runtime/eval summary。
- 关键路径支持失败样本回放。

### Batch 5 - Learner Twin

- 用 evidence graph + learning_event_log + profile_revision 聚合长期学生记忆。
- 区分事实、推断、过期信号和教师可见摘要。
- 推荐和导学引用 Learner Twin 时必须给出证据来源。

### Batch 6 - Realtime / Multimodal

- 语音导学只作为受控入口，输出仍落结构化 tool/event。
- 不给完整可复制答案；引用课件或标明通识知识。
- 前端支持语音状态、转写、引用与安全拦截反馈。

### Batch 7 - Web Research

- 外部搜索只服务最新生态/工具解释和教师侧资料拓展。
- 课程答案仍优先课程包证据；外部内容必须标明非课件来源。
- 加入来源白名单、引用和缓存。

### Batch 8 - Frontend Workbench

- 工坊改为 ChatGPT / Claude 类工作台：会话历史、消息流、工件面板、引用面板、工具调用时间线、评测状态。
- 适配移动端；不做营销页。
- 前端 Vitest + Playwright 点击流验收。

### Batch 9 - Changelog and Commits

- 每批验证后只提交本批文件。
- 最后维护 `CHANGELOG.md`，列明实现、验证、未覆盖风险和比赛演示影响。

## Verification Gates

- Backend focused tests: resource workshop, event mirror, profile listener, evidence graph.
- RAG tests: query API contract, graph route matrix, golden retrieval.
- Frontend tests: typecheck, component tests, browser click-through.
- Full stack smoke: `/healthz` plus core AI workshop flow when frontend batch lands.
