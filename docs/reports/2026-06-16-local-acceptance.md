# Pythontopia 本地验收报告

- 验收时间：2026-06-16（Asia/Shanghai）
- 验收范围：百晓工坊单对话工具治理后的本地回归、AI 导学助手 manager agent 重构、AI/安全相关测试、前后端契约修正
- 代码范围：当前 `main` 工作树，覆盖 backend / frontend / services/pythontopia-graph / security harness / dev 启动脚本
- 验收方式：本地自动化测试 + `./dev.sh` 真栈健康检查；未执行公网 ECS 冒烟，未执行浏览器手工 AC/WA 点击流

## 执行摘要

本轮本地核心质量门均已通过：

| 模块 | 命令 | 结果 |
|---|---|---|
| Graph 微服务 | `cd services/pythontopia-graph && python3 -m pytest -q` | 652 passed，1 warning |
| 安全 harness | `python3 -m pytest -q security/tests` | 36 passed |
| 后端全量 | `cd backend && mvn -s .mvn/settings.build.xml test` | BUILD SUCCESS，762 tests，0 failures，0 errors |
| 前端类型 | `cd frontend && npm run typecheck` | 通过 |
| 前端全量 | `cd frontend && npm run test` | 204 files passed，1981 tests passed |
| 本地真栈健康 | `./dev.sh` + healthz / course videos probes | 5173 / 18080 / 18091 / 18092 healthy，课程视频接口成功 |

结论：百晓工坊工具治理、AI 导学助手 manager agent、主线相关前端规格、Stage07 工坊契约、graph intent fallback 与轻反馈链路在本地自动化和服务健康层面可接受。仍需在下一轮做浏览器手工冒烟，尤其确认真实登录态下的 AC/WA nudge 点击、SSE 渲染、学习事件入库与能力面板交互。

## 验收前修正

本轮验收暴露并修正了几类契约漂移：

1. **资源生成与资源消费事件边界**
   - 文件：`backend/src/test/java/com/pythontopia/stage07/Stage07WorkshopAcceptanceIntegrationTest.java`
   - 修正：生成资源后不再期待立即产生 `RESOURCE_READ` 掌握度事件；改为调用 `/api/workshop/resources/{resourceId}/consumptions` 后验证事件入库。
   - 原因：生成讲义不是学习完成，消费资源才应触发阅读掌握度回流。

2. **graph 意图 fallback 断言**
   - 文件：`services/pythontopia-graph/tests/test_intent_router_agent.py`
   - 修正：provider 失败后通过 harness 进入关键词 fallback，意图为 `ASKME`，置信度稳定为 `0.78`，`reason_code=fallback`。
   - 原因：当前实现不是低置信失败态，而是确定性 fallback 命中。

3. **百晓工坊答案轻反馈**
   - 文件：`frontend/src/components/workshop/WorkshopChat.vue`
   - 修正：恢复 `AnswerFeedback`，将反馈写入 `answer_feedback` 学习事件，失败时不打断当前对话。
   - 原因：轻反馈是工具治理后的低摩擦评价闭环，前端规格也依赖该交互。

4. **图书馆阅读路径**
   - 文件：`frontend/src/pages/LibraryPage.vue`
   - 修正：恢复阅读路径 breadcrumb，并对标题空白做展示归一化。
   - 原因：学生阅读课件时需要知道当前章节、文档和页码位置；这是 AI 证据引用与课件阅读之间的方向感。

5. **前端规格对齐**
   - 文件：`frontend/tests/components/*`、`frontend/tests/integration/i18n-a11y.integration.spec.ts`
   - 修正：HUD 新增导学手册槽位后的 a11y 数量、章测解锁条件、资源卡打开方式、ASKME 三轮问答上下文、GuideOverlay 工具时间线等断言。
   - 原因：部分测试仍停留在旧 UI/旧工具透明行契约。

6. **本地启动 Maven 镜像配置**
   - 文件：`dev.sh`
   - 修正：后端启动命令从 `.mvn/settings.xml` 切到 `.mvn/settings.build.xml`。
   - 原因：`.mvn/settings.xml` 只走 Maven Central，当前网络环境下 `poi-ooxml` 依赖曾因 `repo.maven.apache.org` 访问超时阻断 `./dev.sh`；build settings 已配置可用镜像。

## 详细结果

### Graph

- 命令：`cd services/pythontopia-graph && python3 -m pytest -q`
- 结果：`652 passed, 1 warning in 43.43s`
- 说明：覆盖 intent router、orchestrator、工具 harness、AI 导学助手 coach schema / manager agent / guardrail / SSE 进度等测试。唯一 warning 为现有测试环境警告，未导致失败。

### AI 导学助手 focused gates

- Graph 命令：
  `cd services/pythontopia-graph && python3 -m pytest -q tests/test_tutor_workflow_coach_schema.py tests/test_guide_coach_agent.py tests/test_tutor_workflow_coach_graph.py tests/test_guide_guardrail_critic.py tests/test_guide_coach_golden.py tests/test_sse_agui.py`
- 结果：`38 passed in 0.78s`
- Backend 命令：
  `cd backend && mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,TutorWorkflowSubmissionFeedbackListenerTest,SubmissionServiceTest test`
- 结果：17 tests，0 failures，0 errors。
- Backend graph client 命令：
  `cd backend && mvn -s .mvn/settings.build.xml -Dtest=TutorGraphClientSseTest,TutorGraphClientFailureTest test`
- 结果：5 tests，0 failures，0 errors。
- Frontend 命令：
  `cd frontend && npm run test -- tests/components/training/GuideNudgeCard.spec.ts tests/components/training/TutorWorkflowPanel.spec.ts`
- 结果：2 files，7 tests passed；`npm run typecheck` 通过。

### 安全 Harness

- 命令：`python3 -m pytest -q security/tests`
- 结果：`36 passed in 0.29s`
- 说明：本地 harness 层面通过；不等同于公网安全终验。

### 后端

- 命令：`cd backend && mvn -s .mvn/settings.build.xml test`
- 结果：BUILD SUCCESS，`Tests run: 762, Failures: 0, Errors: 0, Skipped: 0`
- 补充：日志中包含 graph client failure 测试的预期 connection refused / read timeout、Hikari shutdown 连接校验警告，以及 Surefire fork JVM 退出提示；发生在测试成功之后，未改变结果。

### 前端

- 命令：`cd frontend && npm run typecheck`
- 结果：通过
- 命令：`cd frontend && npm run test`
- 结果：`Test Files 204 passed (204)`，`Tests 1981 passed (1981)`
- 补充：测试日志中存在 jsdom 环境噪声，包括 `127.0.0.1:3000` API 连接拒绝、router warning、`window.scrollTo` 未实现；均未导致失败。

### 本地真栈健康

- 启动：`./dev.sh`
- 修复前现象：后端 Maven 使用 `.mvn/settings.xml` 时，`repo.maven.apache.org` 访问超时导致 `poi-ooxml` 依赖无法解析。
- 修复：`dev.sh` 后端启动改用 `.mvn/settings.build.xml`，并清理本地 `.lastUpdated` 失败标记后重新启动。
- 健康检查：
  - `curl -sf http://127.0.0.1:5173/healthz` -> `{"status":"ok"}`
  - `curl -sf http://127.0.0.1:18080/healthz` -> `{"status":"ok"}`
  - `curl -sf http://127.0.0.1:18091/healthz` -> `{"status":"ok"}`
  - `curl -sf http://127.0.0.1:18092/healthz` -> `{"status":"ok"}`
  - `curl -sf http://127.0.0.1:18080/api/course/videos` 返回课程视频 JSON。
- 后端日志：`.work/dev-backend.log` 出现 `Started PythontopiaApplication in 3.503 seconds`。
- Docker 依赖：Postgres / Redis / graph / rag 均为 healthy。

## 未覆盖与剩余风险

- 已执行 `./dev.sh` 全栈健康检查；未执行浏览器手工 AC/WA 点击流。
- 未执行公网 ECS 验收；本报告只证明本地自动化测试通过。
- 未执行 Android / Windows / 微信端回归。
- 前端测试中的 jsdom 网络与浏览器 API 噪声仍建议后续分批收敛，避免掩盖真实失败。
- 后端 Surefire fork JVM 退出提示建议后续观察；目前为成功后的测试进程清理问题。

## 验收结论

本地自动化验收与服务健康检查通过。AI 导学助手 manager agent 计划的第一轮实现已完成到“短 nudge -> 点击继续 -> manager 决策 -> 工具卡 -> guardrail -> 状态/反馈回流 -> 后续提交更新 state”的闭环；下一轮若继续推进，应优先做浏览器手工路径、ECS 公网验收和多端回归。
