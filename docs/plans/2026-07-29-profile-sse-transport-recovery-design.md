# Profile SSE transport recovery 迁移设计

日期：2026-07-29

状态：已完成（TDD、共享回归与后端全量证据已收口）

## 背景

Profile 是剩余两个 `GraphSseConsumer` 调用方中风险较低的一条：前端当前画像访谈使用同步
`POST /turn`，流端点尚未被 Web 主链路调用；但后端公开流接口仍应满足同一合同。

当前 `ProfileService.sendTurnStream` 有两处真实分叉：

- Python `/agents/profile/turn/stream` 已经用 `sse_agui.translate("result", ...)` 发送 canonical
  `RUN_FINISHED`，data 为 `{"result":{...}}`；Java 只在 legacy `result` 时投影，canonical 成功流会漏掉
  profile upsert、8 维镜像、revision、访谈完成和 resource run；
- 旧 consumer 抛 IOException 时会调用同步 `sendTurn` 重试。公共 `GraphStreamTransport` 目前在失败时
  自行发送 error；若业务随后再 fallback，会形成 error + result 双终态。

Profile 的同步 fallback 仍调用同一个 ProfileAgent，并非降级为普通聊天，符合“Agent 故障 fail fast、
不静默伪造”的约束；因此迁移时保留一次显式 recovery，而不是直接删除 fallback。

## 目标

1. 为 `GraphStreamTransport` 增加可选的显式失败 recovery；
2. recovery 成功时由 transport 发送其返回的唯一终态，禁止先发 error；
3. recovery 未处理、返回非法终态或抛错时，仍发送原有唯一净化 error；
4. Profile 流改用公共 transport，保留同步 `sendTurn` fallback；
5. Profile 同时识别 legacy `result` 与 canonical `RUN_FINISHED.result`，成功投影恰好一次；
6. 连接/读取超时对齐现有 Profile `SseEmitter` 120 秒，客户端断开停止上游。

## 非目标

- 不迁移 Workshop；
- 不修改前端画像访谈从同步 API 切到 SSE；
- 不改变同步 `sendTurn` 业务、画像 DTO、数据库写入顺序或 resource run 口径；
- 不新增重连、首包独立 deadline、序号、`Last-Event-ID` 或 heartbeat 生成；
- 不让 recovery 直接操作 complete 生命周期，也不允许它返回非终态事件。

## 方案

### Transport recovery 合同

在 `GraphStreamTransport` 增加：

```java
enum FailureKind { UNAVAILABLE, TIMEOUT, INTERRUPTED }
record TerminalEvent(String event, Object data) {}
interface FailureRecovery {
  TerminalEvent recover(FailureKind failure) throws Exception;
}
```

- 保留当前 6 参数 `relay(...)`，内部委托新 overload 且 recovery 为 null，现有三个调用方零改动；
- 新 overload 在 HTTP/连接错误、读超时或无终态 EOF 时调用 recovery；
- recovery 返回 `result/error/RUN_FINISHED/RUN_ERROR` 之一，transport 负责唯一一次 emitter send；
- 返回 null/非法事件/抛错则记录服务端日志并发送对应的既有净化 error；
- 客户端断开不触发 recovery，上游已经发 `error/RUN_ERROR` 也不触发 recovery；
- 所有路径仍由 transport finally 断开连接并 complete 一次。

该边界让业务只负责“能否生成替代终态”，transport 继续拥有 wire send、单终态和生命周期。

### Profile adapter

- `ProfileService` 注入 `GraphHttpClientProperties` 并创建 read timeout 120 秒的 transport；提供包内低超时
  构造供真流测试；
- 成功旁路：legacy `result` 直接投影；`RUN_FINISHED` 先提取 object `result` 再投影，emitter 仍原样
  透传 canonical wrapper；
- failure recovery：调用现有同步 `sendTurn`；成功则序列化返回 legacy `result` 终态。`sendTurn` 已完成
  upsert/mirror/revision/resource run，recovery 不再调用 `handleTurnResult`，避免双写；
- fallback 异常记录服务端原因并返回 null，由 transport 发送对应净化 error；不再
  `completeWithError` 裸断流。

## 验收

### RED

- 本地 Graph 返回 canonical `RUN_FINISHED`：旧代码透传但不 upsert/complete interview；
- 上游 HTTP 失败、同步 fallback 成功：旧 transport 尚无 recovery overload，预期编译 RED；
- fallback 失败：必须恰好一个净化 error、无 result、无 `completeWithError`。

### GREEN

- canonical 成功原样透传，画像 upsert/mirror/revision/resource run 恰好一次；
- fallback 成功只有一个 legacy result，画像副作用恰好一次，无 error；
- fallback 失败只有一个与 failure kind 对应的净化 error；
- 上游 RUN_ERROR 原样终止且不触发 fallback；
- Profile 定向、共享 transport 30 项、Spring 上下文与 Backend 全量按真实结果记录。

## 风险与回滚

- 风险：canonical 成功流此前漏写，修复后会真正完成访谈；合同测试固定一次性副作用，防重复。
- 风险：recovery 返回终态的信任边界扩大；transport 白名单校验终态事件，非法返回自动回到净化 error。
- 回滚：恢复 Profile 旧 consumer，并删除 recovery overload/目标测试；同步 API 与数据迁移无需回滚。
