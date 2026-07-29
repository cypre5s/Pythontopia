# AG-UI 生命周期身份契约设计

日期：2026-07-29

状态：已确认，待 TDD 实施

## 背景

主 orchestrator 流已经发送 `RUN_STARTED` 与 `RUN_FINISHED`，但生命周期身份不完整：

- `RUN_STARTED.runId` 固定为 `orchestrator:{session_id}`，同一会话多轮请求会重复；
- `RUN_FINISHED` 只有 `result`，没有与开始事件对应的 `threadId/runId`；
- 前端协议层解析两个事件时会丢弃已有身份字段。

AG-UI 当前官方事件合同要求 `RUN_STARTED` 与 `RUN_FINISHED` 都携带 `threadId` 和
`runId`。其中 thread 表示长期会话，run 表示会话内一次独立执行。当前实现把两者合并，
会使标准客户端、运行追踪和并发请求无法可靠关联开始与终态。

官方参考：

- <https://docs.ag-ui.com/sdk/js/core/events>

## 当前仓库事实

- `OrchestratorRequest.session_id` 是前后端已有的长期会话身份；
- `OrchestratorGraph` 内部的 `turn_thread_id` 要在读取会话历史后才能确定，不适合在路由
  提前发送 `RUN_STARTED` 时作为 run 身份；
- `_orchestrator_stream_generator` 是本切片唯一改动的生产流；
- `sse_agui.translate("result", ...)` 被多个其他端点复用，不能全局强制它们立即补齐身份；
- Java 中转和运行记录器按事件名及 `result` 字段消费，多出的身份字段不会破坏现有行为；
- 前端已有旧事件与部分 canonical 事件兼容，内部类型仍需允许滚动发布期间缺失身份的旧流。

## 目标

1. `threadId` 明确等于请求的 `session_id`；
2. 每次 `/agents/orchestrator/turn/stream` 请求生成唯一 `runId`；
3. 同一次流的 `RUN_STARTED` 与 `RUN_FINISHED` 携带完全相同的身份；
4. 前端保留并暴露 canonical 生命周期身份；
5. 旧端点和旧载荷继续可用，不做全仓 transport 大改。

## 非目标

- 不修改 tool-loop、tutor、workflow 等其他流式端点；
- 不把 LangGraph checkpoint 的 `session_id:turn-N` 暴露为外部 run 身份；
- 不统一心跳、超时、取消、错误终态或 SSE transport；
- 不修改 Java 中转、数据库、API 路径、鉴权或业务结果；
- 不移除前端对历史事件名和缺身份旧事件的兼容。

## 方案

### 请求级身份

主路由在生成器开始时创建：

```text
threadId = body.session_id
runId    = "orchestrator:" + uuid4().hex
```

UUID 不依赖会话历史读取，也不共享可变计数器，因此同一 session 的串行或并发请求都不会
复用 run 身份。`runId` 不拼接完整 session，避免 session 最大长度放大协议字段。

### 有界翻译上下文

`sse_agui.translate` 增加可选的 `thread_id/run_id` 上下文：

- 有上下文的 `result` 编码为包含 `threadId/runId/result` 的 canonical `RUN_FINISHED`；
- 无上下文时保持当前 `{"result": ...}` 输出，避免一次改动扩散到其他端点；
- `RUN_STARTED` 由主 orchestrator 直接使用同一组身份生成。

该兼容分支只服务滚动迁移；主 orchestrator 的路由测试必须验证 canonical 精确合同。

### 前端边界

`AguiRunStarted` 与 `AguiRunFinished` 增加可选 `threadId/runId`。之所以在内部兼容类型上保持
可选，是因为同一 adapter 仍消费历史端点；canonical 主流是否完整由生产端与协议测试强制。
`toAguiEvent` 对官方事件保留身份，对历史 `result` 继续产生无身份终态。

## 验收

### RED

- helper 测试要求带上下文的 `RUN_FINISHED` 输出两个身份字段；
- 主路由测试解析 SSE，要求开始与结束身份相同；
- 同一 `session_id` 连续请求两次，要求 `threadId` 相同而 `runId` 不同；
- 前端 canonical 流测试要求归一化事件保留身份。

### GREEN

- `tests/test_sse_agui.py` 与 `tests/test_orchestrator_route.py` 通过；
- `frontend/tests/composables/useSseStream.spec.ts` 通过；
- Graph 与前端全量测试、TypeScript strict、lint 和生产构建按影响范围通过；
- 既有无上下文 helper、历史 `result` 和其他流端点契约不回归。

## 风险与回滚

- 风险：内部前端类型为兼容旧流而把身份设为可选，不能单靠 TypeScript 证明 wire contract。
  通过 canonical 路由与解析测试把完整性固定在边界。
- 风险：错误终态仍没有成对身份。本切片只收敛成功生命周期，统一错误终态留给 transport 小步。
- 回滚：回退路由上下文、helper 可选参数和前端字段保留即可；没有持久化迁移。
