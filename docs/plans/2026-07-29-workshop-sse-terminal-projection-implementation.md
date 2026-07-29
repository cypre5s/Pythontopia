# Workshop SSE 终态投影迁移实施计划

> 执行准则：先固定 Workshop 现有成功投影和真实失败缺口，再扩展最小终态映射边界。

**目标：** 清除最后一个生产 `GraphSseConsumer` 调用方，同时保持资源持久化后的 legacy result 合同。

**技术栈：** Java 21、Spring Boot MVC、JUnit 5、Mockito、AssertJ、JDK `HttpServer`。

---

## Task 1：建立 Workshop 真流 RED

**新增：**

- `backend/src/test/java/com/pythontopia/workshop/graph/WorkshopGraphClientStreamTest.java`

先固定 canonical 成功映射为 legacy result 的既有行为；再加入 trace 后 EOF、fallback 双收口、上游
RUN_ERROR、result 处理失败与低 read timeout 场景。确认旧实现至少得到稳定的 EOF 行为 RED、
`completeWithError` RED 和构造合同编译 RED。

## Task 2：扩展 transport 终态映射边界

**修改：**

- `backend/src/main/java/com/pythontopia/common/sse/GraphStreamTransport.java`；
- 公共 transport 真流测试。

保留现有 overload；新增 `TerminalEventMapper` 与完整 overload。只对上游终态做发送前映射，验证映射
结果白名单；映射异常只发净化终态且禁止 recovery，非终态和 mapper=null 路径保持不变。

## Task 3：迁移 WorkshopGraphClient

**修改：**

- `backend/src/main/java/com/pythontopia/workshop/graph/WorkshopGraphClient.java`；
- 既有手工构造该客户端的测试。

接入 Graph connect timeout + Workshop 180 秒 read inactivity timeout；用 terminal mapper 处理
canonical/legacy 落库投影，用 recovery 保留同步 `produce` fallback；删除旧 consumer 与重复生命周期。

## Task 4：回归与收口

```bash
cd backend
mvn -q -Dtest='WorkshopGraphClientStreamTest,WorkshopGraphClientTest,ResourceWorkshopServiceTest,GraphStreamTransportRecoveryTest,ProfileServiceStreamTest,TutorGraphClientFailureTest,OrchestratorGraphClientFailureTest' test
mvn -q -Dtest=PythontopiaApplicationTests test
mvn test
```

运行变更文件 Spotless、`git diff --check`，同步 API contract/worklog/changelog；全量环境错误按已确认的
TIME_WAIT 方法逐类新鲜复跑，最后只暂存本切片文件并审计 staged diff。
