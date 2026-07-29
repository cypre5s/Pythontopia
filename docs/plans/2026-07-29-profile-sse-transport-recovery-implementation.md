# Profile SSE transport recovery 实施计划

> 执行准则：先固定 canonical 投影与单终态 fallback，再扩展公共 transport；不改同步画像业务。

**目标：** Profile 流接入 `GraphStreamTransport`，保留显式同步 recovery 且任何路径只有一个终态。

**技术栈：** Java 21、Spring Boot MVC、JUnit 5、Mockito、AssertJ、JDK `HttpServer`。

---

## Task 1：建立 Profile 真流 RED

**新增：**

- `backend/src/test/java/com/pythontopia/learner/profile/service/ProfileServiceStreamTest.java`

先用当前可构造服务启动本地 Graph：

1. 返回 `RUN_FINISHED` wrapper 与完整 ProfileTurnResponse；
2. 断言事件原样透传，但 repository upsert/complete 必须发生；
3. 运行单测试，确认旧代码只识别 `result`，得到稳定行为 RED。

随后补 fallback 成功/失败与低 timeout 构造合同，旧 transport/recovery 接线缺失时得到编译 RED。

## Task 2：扩展公共 recovery 边界

**修改：**

- `backend/src/main/java/com/pythontopia/common/sse/GraphStreamTransport.java`

步骤：

1. 新增 `FailureKind`、`TerminalEvent`、`FailureRecovery`；
2. 保留原 relay 签名并委托新 overload；
3. 三类服务端失败先尝试 recovery；
4. 只允许四种终态事件，transport 负责发送；
5. recovery null/非法/异常继续发原净化 error；客户端断开与上游终态不调用 recovery。

## Task 3：迁移 Profile adapter

**修改：**

- `backend/src/main/java/com/pythontopia/learner/profile/service/ProfileService.java`；
- 两个手工构造 ProfileService 的既有测试。

接入 Graph HTTP connect timeout + 120 秒 read timeout；成功旁路兼容 legacy/canonical；recovery 复用
`sendTurn` 并返回 legacy result；删除旧 consumer try/catch/completeWithError 生命周期。

## Task 4：回归与收口

```bash
cd backend
mvn -q -Dtest='ProfileServiceStreamTest,ProfileServiceFailureTest,InterviewDimensionMirrorTest,TutorWorkflowGraphClientTest,TutorGraphClientFailureTest,OrchestratorGraphClientFailureTest' test
mvn -q -Dtest=PythontopiaApplicationTests test
mvn test
```

运行变更文件 Spotless、`git diff --check`，同步 API contract/worklog/changelog；对全量环境错误按已确认
TIME_WAIT 方法逐类新鲜复跑，最后窄暂存并审计 staged diff。
