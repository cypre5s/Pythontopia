# Pythontopia 本地验收报告

- 验收时间：2026-06-16（Asia/Shanghai）
- 验收范围：百晓工坊单对话工具治理后的本地回归、AI/安全相关测试、前后端契约修正
- 代码范围：当前 `main` 工作树，覆盖 backend / frontend / services/pythontopia-graph / security harness
- 验收方式：本地自动化测试为主，未执行公网 ECS 冒烟，未执行完整 `./dev.sh` 浏览器真栈验收

## 执行摘要

本轮本地核心质量门均已通过：

| 模块 | 命令 | 结果 |
|---|---|---|
| Graph 微服务 | `cd services/pythontopia-graph && python3 -m pytest -q` | 632 passed，1 warning |
| 安全 harness | `python3 -m pytest -q security/tests` | 36 passed |
| 后端全量 | `cd backend && mvn -s .mvn/settings.build.xml test` | BUILD SUCCESS，756 tests，0 failures，0 errors |
| 前端类型 | `cd frontend && npm run typecheck` | 通过 |
| 前端全量 | `cd frontend && npm run test` | 202 files passed，1973 tests passed |

结论：百晓工坊工具治理、主线相关前端规格、Stage07 工坊契约、graph intent fallback 与轻反馈链路在本地自动化层面可接受。仍需在下一轮做真栈浏览器冒烟，尤其确认 SSE、真实登录态、学习事件入库与能力面板交互。

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

## 详细结果

### Graph

- 命令：`cd services/pythontopia-graph && python3 -m pytest -q`
- 结果：`632 passed, 1 warning in 45.12s`
- 说明：覆盖 intent router、orchestrator、工具 harness、教育 agent 相关测试。唯一 warning 为现有测试环境警告，未导致失败。

### 安全 Harness

- 命令：`python3 -m pytest -q security/tests`
- 结果：`36 passed in 0.29s`
- 说明：本地 harness 层面通过；不等同于公网安全终验。

### 后端

- 命令：`cd backend && mvn -s .mvn/settings.build.xml test`
- 结果：BUILD SUCCESS，`Tests run: 756, Failures: 0, Errors: 0, Skipped: 0`
- 补充：终止阶段出现 scheduler / Hikari shutdown 日志，以及 Surefire fork JVM 超时自杀提示；发生在测试成功之后，未改变结果。

### 前端

- 命令：`cd frontend && npm run typecheck`
- 结果：通过
- 命令：`cd frontend && npm run test`
- 结果：`Test Files 202 passed (202)`，`Tests 1973 passed (1973)`
- 补充：测试日志中存在 jsdom 环境噪声，包括 `127.0.0.1:3000` API 连接拒绝、router warning、`window.scrollTo` 未实现；均未导致失败。

## 未覆盖与剩余风险

- 未执行 `./dev.sh` 全栈启动与浏览器真栈验收。
- 未执行公网 ECS 验收；本报告只证明本地自动化测试通过。
- 未执行 Android / Windows / 微信端回归。
- 前端测试中的 jsdom 网络与浏览器 API 噪声仍建议后续分批收敛，避免掩盖真实失败。
- 后端 Surefire fork JVM 退出提示建议后续观察；目前为成功后的测试进程清理问题。

## 验收结论

本地自动化验收通过，可以进入下一阶段：加载并批判性 review AI 导学助手 manager agent 调度教育工具重构计划，选择第一批低风险、高可验证的实现任务推进。
