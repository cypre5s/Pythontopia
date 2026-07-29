# Workshop SSE 终态投影迁移设计

日期：2026-07-29

状态：已确认，待 TDD 实施

## 背景

Workshop 是后端最后一个 `GraphSseConsumer` 生产调用方。它已经兼容 Python 资源流的 canonical
`RUN_FINISHED.result`，但仍自行持有 HTTP、fallback、emitter 与 complete 生命周期，存在三处缺口：

- 上游在终态前 EOF 时，旧 consumer 只结束读取，Workshop 会静默 `complete()`，不触发同步恢复，也不发
  error；前端最终得到“done 但无 resourceId”；
- 客户端断开只吞掉 emitter 写异常，仍继续消费昂贵的资源图；读超时也不可在测试中注入；
- 同步 fallback 再失败时先发 `RESOURCE_STREAM_FALLBACK_FAILED`，随后又
  `completeWithError`，wire error 与裸 Servlet 错误同时存在。

Workshop 不能直接复用当前 transport 的旁路 callback：Graph 的成功终态是完整资源草稿，Java 必须先
执行 `onResult` 写入 resource run 与 learning resource，生成 `resourceId`，再以新的 legacy `result`
替换原 `RUN_FINISHED`。若先透传原终态，前端会把 Graph 草稿误当 `ResourceCreateResponse`。

## 目标

1. 为 `GraphStreamTransport` 增加发送前的可选终态映射边界；
2. 非终态事件继续原样透传，现有调用方零行为变化；
3. Workshop canonical/legacy 成功终态都先持久化，再只发送一个 legacy `result`；
4. 连接失败、读不活动超时和无终态 EOF 保留一次同步 `produce` recovery；
5. result 处理失败与 fallback 失败各只发一个业务脱敏 error，不裸 `completeWithError`；
6. 客户端断开立即停止上游，所有路径仍由 transport 完成连接与 emitter 收口。

## 非目标

- 不修改 `ResourceWorkshopService` 的资源落库顺序、发布门禁、DTO 或数据库；
- 不把同步 `produce` 改成异步状态机；附件 P0-3 的长事务拆分留作独立高风险切片；
- 不改 Python `ResourceGraph`、前端 `useSseStream` 或现有 legacy `result` 客户端合同；
- 不开放任意非终态事件重写，不新增重连、首包 deadline、序号或 `Last-Event-ID`；
- 不在本切片删除已无生产调用方的 `GraphSseConsumer`，先做源码引用与测试用途审计。

## 方案

### Transport 终态映射合同

在 `GraphStreamTransport` 增加：

```java
interface TerminalEventMapper {
  TerminalEvent map(String event, String data) throws Exception;
}
```

- 保留现有 6 参数与 recovery overload，均委托到新增完整 overload，mapper 缺省为 null；
- decoder 遇到 `result/error/RUN_FINISHED/RUN_ERROR` 后，先调用 mapper，再发送映射后的唯一终态；
- mapper 返回值仍受四种终态白名单约束；null、非法事件或抛错时，transport 记录服务端详情并发送一个
  `AI_STREAM_INTERRUPTED` 脱敏 error；
- mapper 失败时不调用 recovery：此时上游已经成功到达终态，重新执行 Graph 可能重复生成或重复落库；
- 非终态事件不经过 mapper；`onEvent` 观察的是最终实际发送的事件，现有 mapper=null 路径完全不变。

这里只开放终态映射，而不是通用 event transformer：当前真实需求只有 Workshop 的“Graph 草稿 → 已落库
resourceId”；限制能力面可避免业务改写 trace/token/plan 等过程事件。

### Workshop adapter

- `WorkshopGraphClient` 注入 `GraphHttpClientProperties` 与 `WorkshopHttpClientProperties`，transport 使用普通
  Graph connect timeout 和 Workshop 180 秒读不活动上限；现有 300 秒 emitter 保持不变；
- success mapper：legacy `result` 直接处理；canonical `RUN_FINISHED` 先剥 `result` wrapper；`onResult`
  成功后返回 legacy `result`；处理异常返回 `RESOURCE_STREAM_RESULT_FAILED` error；
- transport recovery：调用现有同步 `produce`，成功经同一 `onResult` 落库后返回 legacy `result`；异常
  返回 `RESOURCE_STREAM_FALLBACK_FAILED` error；
- 上游主动 `error/RUN_ERROR` 原样映射，不触发同步 recovery；
- 删除 Workshop 旧 consumer try/catch、手写 emitter send/completeWithError 与重复 error sender。

## 验收

### RED

- 本地 Graph 先发 trace 后无终态 EOF：旧 Workshop 静默 complete，期望同步 recovery 的唯一 result 缺失；
- fallback 再失败：旧代码虽然发业务 error，但同时调用 `completeWithError`；
- 低 read-timeout 真流合同在旧构造器上编译 RED。

### GREEN

- legacy/canonical 成功都只发一个落库后的 legacy result，原 Graph 草稿不外泄；
- 截断/超时 recovery 成功时保留此前 trace，并只追加一个 result；
- recovery 失败只有一个 `RESOURCE_STREAM_FALLBACK_FAILED`，complete 恰好一次；
- result 处理失败只有一个 `RESOURCE_STREAM_RESULT_FAILED`，不触发同步 recovery；
- 上游 RUN_ERROR 原样终止且不触发 recovery；客户端断开不触发 recovery；
- Workshop、公共 transport、Profile、Tutor/Orchestrator、Spring 上下文与 Backend 全量按真实结果记录。

## 风险与回滚

- 风险：终态映射是公共 transport 的新扩展点；以终态白名单、mapper-only-on-terminal 和映射失败不恢复
  三道边界限制影响面，并由公共真流测试固定；
- 风险：read timeout 从旧请求总窗口变为 socket 不活动窗口；阈值仍为 180 秒，过程 trace 会合理续命，
  比旧总时限更适合多步骤资源图；
- 回滚：恢复 Workshop 旧 consumer，并删除 terminal mapper overload 与目标测试；Profile 与其他三调用方
  继续使用现有 transport/recovery，不需要回滚。
