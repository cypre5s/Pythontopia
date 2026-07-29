# Java SSE 公共帧解码器设计

日期：2026-07-29

状态：已确认，待 TDD 实施

## 背景

Java 后端当前有两条 Graph SSE 读取路径：

- `common.sse.GraphSseConsumer`：JDK `HttpClient`，仍用于工坊、画像访谈和 TutorWorkflow；
- `tutor.GraphStreamRelay`：`HttpURLConnection`，用于 Tutor 与 Orchestrator，已经具备读超时、
  客户端断开传播、截断保护和单终态错误。

两者各自复制了 `event:` / `data:` / 空行解析循环，而且行为已经分叉：

- 多个 `data:` 行被直接拼接，丢失标准要求的换行；
- `GraphSseConsumer` 会把 EOF 前未以空行结束的尾帧当作完整事件；
- `GraphStreamRelay` 会丢弃尾帧，并由上层补中断终态；
- 注释心跳虽被偶然忽略，但没有独立合同测试；
- 上层的终态、fallback、落库和 emitter 生命周期与帧解析耦合，迁移风险高。

WHATWG HTML Living Standard 明确要求：`data` 字段逐行追加 LF，空行才分发事件，注释行忽略，
到达 EOF 时未分发的数据必须丢弃。

官方参考：

- <https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation>

## 目标

1. 建立一个位于 `common.sse` 的唯一 SSE 帧解码器；
2. `GraphSseConsumer` 与 `GraphStreamRelay` 都复用该解码器，不再复制行循环；
3. 多行 data、注释心跳、默认 `message`、空行分发和 EOF 半帧行为符合标准；
4. handler 可以请求停止读取，供 Relay 在终态或客户端断开时立即关闭上游；
5. 保持现有 HTTP 客户端、超时、错误载荷、fallback 和业务回调边界不变。

## 非目标

- 本切片不把 `GraphSseConsumer` 的三个调用方迁移到 `GraphStreamRelay`；
- 不移动或重命名 `GraphStreamRelay`，不删除旧 consumer；
- 不新增重连、`Last-Event-ID`、序号、首包超时、统一 deadline 或 heartbeat 生成；
- 不修改公开 API 路径、SseEmitter 超时、AG-UI 事件名或业务结果；
- 不把 MVC 改成 WebFlux，也不引入第三方 SSE parser。

## 方案

### 公共解码器

新增 `common.sse.SseFrameDecoder`：

```java
DecodeResult decode(BufferedReader reader, Predicate<SseFrame> handler)
```

- `SseFrame` 只包含 `event` 与 `data`；空 event 映射为默认 `message`；
- 每个 `data:` 字段去掉冒号后的至多一个前导空格，字段之间以 `\n` 连接，不删除其他空白；
- `: ...` 注释、未知字段和无 data 的空事件不分发；
- 只有空行完成帧；EOF 尚有 data 时返回 `TRUNCATED` 且不调用 handler；
- handler 返回 false 时返回 `STOPPED`，否则完整 EOF 返回 `END_OF_STREAM`。

解码器不记录日志、不发送 emitter、不识别 AG-UI 终态，也不决定 fallback；这些仍是 transport
和业务 adapter 的职责。

### 两条路径接入

- `GraphSseConsumer` 把 frame 转为既有 `(event, data)` callback；callback 异常仍记录并继续，
  `TRUNCATED` 只记录 warning，不再把半帧上送；是否补终态留给后续调用方迁移；
- `GraphStreamRelay` 在 handler 内发送 emitter、执行 peek、识别终态；终态或客户端断开返回 false，
  用局部结束原因区分 `TERMINAL/CLIENT_GONE`；任何 EOF 仍按现有规则视为上游提前关闭。

## 验收

### RED

- `GraphSseConsumer` 本地 HTTP 真流发送两行 data 和一个 EOF 半帧；当前实现会得到无换行 data，
  并错误分发半帧；
- 公共解码器单测固定多行、注释、默认事件、半帧丢弃和 handler stop；类尚不存在时编译失败是
  第二阶段预期 RED。

### GREEN

- 新 decoder 单测全部通过；
- `GraphSseConsumer` 真流合同测试通过；
- Orchestrator/Tutor 的 Relay 正常、失败、读超时、截断、客户端断开测试全部通过；
- Backend 全量 Maven 测试和 Spotless 按影响范围通过。

## 风险与回滚

- 风险：极少数上游若依赖非标准 EOF 尾帧，旧 consumer 不再分发。Graph 的 `_format_sse` 始终以
  `\n\n` 收束合法帧，故该变化只阻止坏损数据；真流合同覆盖该边界。
- 风险：多行 JSON data 现在包含换行。JSON 允许 token 间空白，语义不变；文本 data 恢复标准语义。
- 回滚：恢复两处局部解析循环并删除 decoder/tests 即可；无 API 或持久化迁移。
