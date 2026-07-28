# 百晓工坊资源图生产超时收口设计

## 生产证据

最终 ECS release 的 HTTPS 227 项复测在首个深闭环 `beforeAll` 中失败。测试对
`POST /api/workshop/resources` 已做一次有界重试，但两次均返回
`WORKSHOP_GRAPH_UNAVAILABLE`。同一时段的 Graph 日志显示：

- Graph 容器始终 healthy；
- 课件 RAG 请求持续返回 200；
- DeepSeek 结构化生成持续返回 200；
- Java 后端却按普通 `graphRestTemplate` 的 30 秒读取窗口断开，并立即发起重复生成。

Python `ResourceGraph` 单次请求包含课件检索、结构化设计、语义接地审查，模型 provider
的既有单次读取合同为 90 秒；把完整资源图限制为 30 秒属于上下游合同错配。

## 方案

1. 不调整普通 `graphRestTemplate`，意图路由、RAG 和短工具调用继续 3 秒连接 /
   30 秒读取 fail-fast。
2. `WorkshopGraphClient.produce` 注入独立 `workshopRestTemplate`：连接仍沿用 3 秒，
   读取窗口为 180 秒，与既有 `GraphSseConsumer` 三分钟上限一致。
3. 前端非流式创建 API 使用 190 秒，为双层 Nginx 和 JSON 映射保留 10 秒。
4. Workshop SSE emitter 从 120 秒对齐到前端容器的 300 秒代理窗口，避免 emitter
   先于资源图结束；Graph 内部三分钟上限与错误事件合同不变。
5. 公网深链夹具单次资源请求观察窗口为 210 秒，runner 仍使用 `--retries=0`；
   只对明确的 `WORKSHOP_GRAPH_UNAVAILABLE` 保留原有一次可见、有界重试。

## 不采用

- 全局 Graph 改为 180 秒：会扩大所有短请求的线程占用与故障半径。
- 继续增加测试重试：会重复触发付费生成，掩盖后端主动在 30 秒断开的真实合同错误。
- 静默返回模板资源：违反 Agent fail-fast、真实业务数据和防假成功约束。

## 验收

- 属性缺失或非正值回落 180000 ms；
- Workshop 专用 RestTemplate 独立读取超时且连接仍快速失败；
- Spring 全上下文唯一注入专用客户端；
- 前端非流式生成使用 190000 ms，SSE emitter 为 300000 ms；
- ECS 上资源生成完成且不再按 30 秒返回 `WORKSHOP_GRAPH_UNAVAILABLE`；
- 最终 HTTPS 227 项必须 0 failed / 0 flaky / 0 skipped，且 runner 无重试。
