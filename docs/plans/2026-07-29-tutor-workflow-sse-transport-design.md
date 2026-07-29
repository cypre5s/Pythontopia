# TutorWorkflow SSE 强中继迁移设计

日期：2026-07-29

状态：已确认，待 TDD 实施

## 背景

Slice 6 已让 Java 两条 Graph SSE 路径共享 WHATWG 帧解码器，但 transport 故障语义仍分叉。
`TutorWorkflowGraphClient` 继续直接调用旧 `GraphSseConsumer`：

- 上游未发 `result/error/RUN_FINISHED/RUN_ERROR` 就关闭时，客户端被静默 `complete()`；
- emitter 写失败仅被 callback 吞掉，后端仍继续读取上游，无法传播取消；
- 流读取没有显式 socket inactivity timeout，JDK request 的 3 分钟设置不能表达首包/帧间不活动合同；
- HTTP、连接或读取异常走 `completeWithError` 裸断流，没有可解析且净化的唯一终态事件。

现有 `GraphStreamRelay` 已在 Tutor 与 Orchestrator 上验证：有界连接/读取超时、客户端断开即关闭
上游、截断保护、旧/AG-UI 终态识别、单条净化 error，以及资源恰好收口一次。

三个旧 consumer 调用方中，TutorWorkflow 适合作为第一刀：它与 Relay 同包、只做事件透传和终态
旁路投影、没有同步 fallback；Python 端还每 10 秒发送 SSE 注释心跳，可安全支撑 180 秒读不活动上限。
Workshop 另有终态改写、落库和同步 fallback，Profile 另有访谈状态副作用，留待后续独立切片。

## 目标

1. `TutorWorkflowGraphClient` 改用既有 `GraphStreamRelay`，不再直接调用 `GraphSseConsumer`；
2. 上游成功终态仍原样透传，`result` / `RUN_FINISHED` 仍把原始 data 交给 `onResult`；
3. 上游提前关闭或超时只发一条净化 `error`，不得把故障当成功 complete；
4. 客户端断开时停止上游读取，复用 Relay 已验证的取消传播；
5. 连接超时复用 `GraphHttpClientProperties`，读不活动上限对齐当前 180 秒 `SseEmitter`。

## 非目标

- 不迁移 Workshop/Profile，不删除 `GraphSseConsumer`；
- 不移动或重命名 `GraphStreamRelay`，不抽象新的通用接口；
- 不修改 TutorWorkflow API、请求体、Graph 路由、事件名、终态 data 形状或数据库投影；
- 不增加重连、序号、`Last-Event-ID`、更短首包 deadline 或 Java 侧 heartbeat；
- 不把 MVC 改为 WebFlux，不切换到 `RestClient/@HttpExchange`。

## 方案

### 客户端接线

- Spring 构造器新增 `GraphHttpClientProperties`，用其 `connectTimeoutMs` 创建 Relay；
- `STREAM_READ_TIMEOUT_MS = 180_000`，与 `TutorWorkflowService` 的 emitter 窗口一致；Python 端
  10 秒注释 ping 会产生 socket 读取，正常长生成不会被误切断；
- 增加包内测试构造器，仅允许测试注入更短 connect/read timeout；生产仍走 Spring 构造器；
- `runStream` 继续由 `SseRelayExecutor` 提交，Relay body supplier 直接返回既有 map；内部 token
  继续走 `GraphInternalTokenSupport.tokenOrNull`；
- Relay 的旁路 callback 只在 `result` / `RUN_FINISHED` 调用 `onResult`，data 不拆包、不改写。

### 故障合同

- 上游连接失败/HTTP 错误：单条 `error`，code `AI_SERVICE_UNAVAILABLE`；
- socket 读不活动超时：单条 `error`，code `AI_SERVICE_TIMEOUT`；
- 上游无终态关闭：保留已到达事件，末尾单条 `error`，code `AI_STREAM_INTERRUPTED`；
- 上游自己发 `error` / `RUN_ERROR`：原样透传后停止，不再补第二条 error；
- emitter 写失败：立即停止 decoder 并断开上游；Relay 最终只调用 `complete()`，不裸
  `completeWithError`。

## 验收

### RED

- 本地 JDK `HttpServer` 发送 trace 后无终态关闭；旧客户端会静默 complete，缺少
  `AI_STREAM_INTERRUPTED`，测试应稳定失败；
- 增加低读超时构造与 stalled upstream 合同；旧类没有该接线，预期先编译失败。

### GREEN

- RUN_FINISHED 原样透传且 `onResult` 恰好一次收到原始 wrapper；请求体与内部 token 保持；
- 提前关闭保留部分 trace，末尾恰好一条 `AI_STREAM_INTERRUPTED`；
- 卡住的上游在测试阈值内结束，保留部分 trace，末尾恰好一条 `AI_SERVICE_TIMEOUT`；
- `TutorWorkflowServiceTest` 与既有 Relay/Tutor/Orchestrator 故障测试全部通过；
- Backend 影响范围与全量回归按真实结果记录。

## 风险与回滚

- 风险：过去被静默视为成功的坏损流现在出现显式 error；这是合同收紧，正常完整终态不变。
- 风险：若 Python 心跳失效且 180 秒内无任何字节，流会主动超时；该行为优于无限占用中继线程，
  且当前路由已有 10 秒心跳测试。
- 回滚：恢复 `TutorWorkflowGraphClient` 对 `GraphSseConsumer` 的局部调用并删除新增客户端测试；
  无 API、数据或迁移回滚。
