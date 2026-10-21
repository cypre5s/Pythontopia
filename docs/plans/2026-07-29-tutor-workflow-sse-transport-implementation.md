# TutorWorkflow SSE 强中继迁移实施计划

> 执行准则：只迁移一个旧调用方；先固定故障合同，再复用已验证 Relay，不改业务载荷与投影。

**目标：** 将 `TutorWorkflowGraphClient` 从 `GraphSseConsumer` 迁移到 `GraphStreamRelay`。

**技术栈：** Java 21、Spring Boot MVC、JUnit 5、AssertJ、JDK `HttpServer`。

---

## Task 1：建立目标调用方 RED

**新增：**

- `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowGraphClientTest.java`

步骤：

1. 本地上游发送完整 trace 后关闭但不发送终态；
2. 断言 trace 保留、末尾恰好一条 `AI_STREAM_INTERRUPTED`、`onResult` 不触发；
3. 运行单测试，确认旧客户端静默 complete 导致预期失败；
4. 增加可控低 read timeout 的 stalled upstream 用例，确认旧类缺少测试接线的编译 RED。

## Task 2：迁移到强 Relay

**修改：**

- `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowGraphClient.java`

步骤：

1. Spring 构造器注入 `GraphHttpClientProperties`；
2. 生产 read timeout 固定为 180 秒，包内构造器允许测试传入短阈值；
3. `runStream` 改为调用 `GraphStreamRelay.relay`；
4. 保留请求 body、内部 token 和 `result/RUN_FINISHED` 原始旁路回调；
5. 删除客户端自己的 try/catch/complete 生命周期，让 Relay 成为唯一收口点。

## Task 3：目标与共享合同 GREEN

测试覆盖：

- RUN_FINISHED 原样透传、回调与认证请求合同；
- 提前关闭的部分事件 + 单终态中断 error；
- 读不活动超时的部分事件 + 单终态 timeout error；
- 既有 TutorWorkflow 投影、Relay 客户端断开和 Tutor/Orchestrator 失败合同。

```bash
cd backend
mvn -q -Dtest='TutorWorkflowGraphClientTest,TutorWorkflowServiceTest,TutorGraphClientFailureTest,OrchestratorGraphClientFailureTest' test
```

## Task 4：完整回归与记录

```bash
cd backend
mvn test
mvn -q -DspotlessFiles='<本切片 Java 文件>' spotless:check
```

同步增量 worklog、跨端 API 契约与 `docs/project/CHANGELOG.md`；记录完整测试、环境阻断、未部署项
和剩余两个旧 consumer 调用方，只暂存本切片文件并审计 staged diff。
