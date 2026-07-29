# Profile SSE transport recovery 实施计划

状态：已完成（2026-07-29）

> 执行准则：先固定 canonical 投影与单终态 fallback，再扩展公共 transport；不改同步画像业务。

**目标：** Profile 流接入 `GraphStreamTransport`，保留显式同步 recovery 且任何路径只有一个终态。

**技术栈：** Java 21、Spring Boot MVC、JUnit 5、Mockito、AssertJ、JDK `HttpServer`。

---

## Task 1：建立 Profile 真流 RED（完成）

**新增：**

- `backend/src/test/java/com/pythontopia/learner/profile/service/ProfileServiceStreamTest.java`

先用当前可构造服务启动本地 Graph：

1. 返回 `RUN_FINISHED` wrapper 与完整 ProfileTurnResponse；
2. 断言事件原样透传，但 repository upsert/complete 必须发生；
3. 运行单测试，确认旧代码只识别 `result`，得到稳定行为 RED。

随后补 fallback 成功/失败与低 timeout 构造合同，旧 transport/recovery 接线缺失时得到编译 RED。

## Task 2：扩展公共 recovery 边界（完成）

**修改：**

- `backend/src/main/java/com/pythontopia/common/sse/GraphStreamTransport.java`

步骤：

1. 新增 `FailureKind`、`TerminalEvent`、`FailureRecovery`；
2. 保留原 relay 签名并委托新 overload；
3. 三类服务端失败先尝试 recovery；
4. 只允许四种终态事件，transport 负责发送；
5. recovery null/非法/异常继续发原净化 error；客户端断开与上游终态不调用 recovery。

## Task 3：迁移 Profile adapter（完成）

**修改：**

- `backend/src/main/java/com/pythontopia/learner/profile/service/ProfileService.java`；
- 两个手工构造 ProfileService 的既有测试。

接入 Graph HTTP connect timeout + 120 秒 read timeout；成功旁路兼容 legacy/canonical；recovery 复用
`sendTurn` 并返回 legacy result；删除旧 consumer try/catch/completeWithError 生命周期。

## Task 4：回归与收口（完成）

```bash
cd backend
mvn -q -Dtest='ProfileServiceStreamTest,ProfileServiceFailureTest,InterviewDimensionMirrorTest,TutorWorkflowGraphClientTest,TutorGraphClientFailureTest,OrchestratorGraphClientFailureTest' test
mvn -q -Dtest=PythontopiaApplicationTests test
mvn test
```

运行变更文件 Spotless、`git diff --check`，同步 API contract/worklog/changelog；对全量环境错误按已确认
TIME_WAIT 方法逐类新鲜复跑，最后窄暂存并审计 staged diff。

## 执行结果

- RED 先证明 canonical `RUN_FINISHED.result` 虽透传但不触发画像副作用；扩展为四场景后，旧实现因
  缺少包内真流接缝和 recovery 构造合同得到稳定编译 RED；
- `GraphStreamTransport` 新增可选 recovery，旧 6 参数调用方保持不变；恢复仅能返回四种终态，失败
  或异常自动回到对应的脱敏 error；
- Profile canonical 成功会原样透传 wrapper，并且 upsert、8 维镜像、revision、resource run、访谈完成
  各一次；同步恢复成功只发一个 legacy `result` 且副作用各一次；
- Profile/transport/Tutor/Orchestrator 聚焦回归 10 类 / 46 项通过，Spring 全上下文 1/1 通过，6 个变更
  Java 文件 Spotless 通过；
- Backend 全量报告 1102 项、0 failures / 7 errors；7 项均为本机 Docker 29 的 PostgreSQL 映射端口
  TIME_WAIT 冲突或其连带连接重置，等待释放后逐类独立复跑 7 类 / 19 项全部通过；
- 未改前端、数据库或 Python 路由，未部署 ECS；剩余 Workshop 旧 consumer 留给下一独立切片。
