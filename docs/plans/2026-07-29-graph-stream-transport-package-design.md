# GraphStreamTransport 公共包归属设计

日期：2026-07-29

状态：已确认，待机械迁移

## 背景

强 SSE 中继最初只服务 Tutor，因此命名和包路径是 `tutor.GraphStreamRelay`。随着 Orchestrator 和
TutorWorkflow 接入，它已经只依赖 Jackson、JDK HTTP、Spring `SseEmitter` 与公共 decoder，不包含
任何 Tutor DTO 或业务规则，却仍迫使 Orchestrator 反向依赖 `tutor`。

Slice 7 后剩余旧 consumer 调用方位于 `workshop` 和 `learner.profile`。若继续直接 import
`tutor.GraphStreamRelay`，会把临时历史归属固化为跨模块依赖，违背附件“建立
`GraphStreamTransport`”和后端模块化单体方向。因此先做纯包归属/命名迁移，再迁剩余业务调用方。

## 目标

1. 将 `tutor.GraphStreamRelay` 提升为 `common.sse.GraphStreamTransport`；
2. Tutor、TutorWorkflow、Orchestrator 三个现有调用方改用公共类型；
3. 保持构造参数、`relay(...)` 方法、错误码、超时、终态、日志和资源收口行为完全不变；
4. 不保留旧包兼容 wrapper，避免形成两套入口；
5. 用既有 30 项目标/共享故障合同和 Spring 全上下文证明机械迁移无行为变化。

## 非目标

- 不在本切片迁移 Workshop/Profile；
- 不改变 `relay(...)` 参数模型，不新增 deadline、首包超时、序号、重连或 heartbeat 生成；
- 不把 transport 注册为 Spring bean，不统一各调用方的 read timeout 值；
- 不修改公开 REST/SSE API、事件名、错误 payload、Graph 路由或数据库；
- 不引入 WebFlux、`RestClient/@HttpExchange` 或第三方 SSE 库。

## 方案

- 新文件：`backend/src/main/java/com/pythontopia/common/sse/GraphStreamTransport.java`；
- 删除：`backend/src/main/java/com/pythontopia/tutor/GraphStreamRelay.java`；
- 类名、构造器名、logger 名和 Javadoc 改为 `GraphStreamTransport`；内部代码逐行保持；
- `TutorGraphClient`、`TutorWorkflowGraphClient`、`OrchestratorGraphClient` 的字段类型、构造调用和
  Javadoc 链接改为公共类型；业务 callback 不动；
- 不新增旧类委托层。仓内类型只在三个生产调用方出现，单提交原子迁移可安全编译。

## 验收

- 迁移前 6 类 / 30 项目标与共享 Relay 合同为绿；
- 迁移后同一命令仍 30/30；
- `rg GraphStreamRelay` 为 0，`GraphStreamTransport` 只出现在公共实现和三个调用方；
- `PythontopiaApplicationTests` 通过，证明 Spring 构造链无漂移；
- 变更 Java 文件 Spotless、`git diff --check` 和 Backend 全量按真实结果记录。

## 风险与回滚

- 风险仅为漏改 import/符号导致编译失败；同提交编译和 30 项合同会立即发现。
- 无运行语义、API 或持久化风险；回滚时恢复旧文件名/包名和三个 import 即可。
