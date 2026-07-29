# Java SSE 公共帧解码器实施计划

> 执行准则：保持 HTTP 与业务策略不变，只统一 wire frame 解释；每一步 TDD 并记录真实命令。

**目标：** 让 `GraphSseConsumer` 与 `GraphStreamRelay` 共享 WHATWG 对齐的帧解码器。

**技术栈：** Java 21、Spring Boot MVC、JUnit 5、AssertJ、JDK `HttpServer`。

---

## Task 1：建立旧 consumer 真流 RED

**新增：**

- `backend/src/test/java/com/pythontopia/common/sse/GraphSseConsumerTest.java`

步骤：

1. 启动本地 JDK `HttpServer` 返回 `text/event-stream`；
2. 发送 complete 多行 data 帧，再发送不带最终空行的半截 result；
3. 断言只收到 complete 帧且 data 为 `first\nsecond`；
4. 运行定向 Maven，确认当前实现无换行并错误分发尾帧。

## Task 2：建立 decoder 单元合同

**新增：**

- `backend/src/main/java/com/pythontopia/common/sse/SseFrameDecoder.java`
- `backend/src/test/java/com/pythontopia/common/sse/SseFrameDecoderTest.java`

测试覆盖：

- 多行 data 以 LF 连接并保留字段有效空白；
- 注释心跳与未知字段忽略；
- 默认事件名在每帧后重置为 `message`；
- EOF 半帧返回 `TRUNCATED` 且不分发；
- handler 返回 false 后立即 `STOPPED`。

## Task 3：接入两套读取路径

**修改：**

- `backend/src/main/java/com/pythontopia/common/sse/GraphSseConsumer.java`
- `backend/src/main/java/com/pythontopia/tutor/GraphStreamRelay.java`

步骤：

1. consumer 用 decoder 转接既有 BiConsumer，保留 callback 异常隔离；
2. relay 用 decoder 转接 emitter/peek/terminal 逻辑，保留结束原因；
3. 删除两份重复行解析循环；
4. 定向测试转 GREEN。

定向命令：

```bash
cd backend
mvn -q -Dtest='GraphSseConsumerTest,SseFrameDecoderTest,OrchestratorGraphClientTest,OrchestratorGraphClientFailureTest,TutorGraphClientSseTest,TutorGraphClientFailureTest' test
```

## Task 4：完整回归与记录

```bash
cd backend
mvn test
mvn spotless:check
```

完成后同步增量 worklog、跨端 API 契约与 `docs/project/CHANGELOG.md`，记录完整测试、未部署项和
下一步迁移边界；仅暂存本切片文件并审计 staged diff。
