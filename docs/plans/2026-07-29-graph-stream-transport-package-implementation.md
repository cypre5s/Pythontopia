# GraphStreamTransport 公共包迁移实施计划

> 执行准则：纯机械包归属与命名迁移，不夹带调用方行为改造。

**目标：** 用唯一 `common.sse.GraphStreamTransport` 替代历史 `tutor.GraphStreamRelay`。

**技术栈：** Java 21、Spring Boot MVC、JUnit 5、Maven、Spotless。

---

## Task 1：固定迁移基线

```bash
cd backend
mvn -q -Dtest='TutorWorkflowGraphClientTest,TutorWorkflowServiceTest,TutorGraphClientSseTest,TutorGraphClientFailureTest,OrchestratorGraphClientTest,OrchestratorGraphClientFailureTest' test
```

记录 6 类 / 30 项基线，覆盖成功、认证、投影、截断、超时、HTTP/连接失败、上游错误与客户端断开。

## Task 2：移动并重命名公共 transport

**新增/删除：**

- 新增 `backend/src/main/java/com/pythontopia/common/sse/GraphStreamTransport.java`；
- 删除 `backend/src/main/java/com/pythontopia/tutor/GraphStreamRelay.java`。

只修改 package/class/constructor/logger/Javadoc 名称，内部常量、方法与控制流不变。

## Task 3：切换三个现有调用方

**修改：**

- `backend/src/main/java/com/pythontopia/tutor/TutorGraphClient.java`；
- `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowGraphClient.java`；
- `backend/src/main/java/com/pythontopia/orchestrator/OrchestratorGraphClient.java`。

改 import、字段类型、构造调用与 Javadoc 链接；不得修改请求、timeout 数值、callback 或 emitter。

## Task 4：守恒验证与记录

1. 重跑 6 类 / 30 项；
2. 运行 `PythontopiaApplicationTests`；
3. `rg` 确认旧符号归零；
4. 变更文件 Spotless 与 `git diff --check`；
5. Backend 全量与必要环境错误隔离复跑；
6. 同步 worklog/changelog，窄暂存并审计 staged diff。
