# Pythontopia 小步重构工作日志

> 开始时间：2026-07-29
> 当前分支：`refactor/workshop-history-account-isolation`
> 总体输入：用户提供的八方向重构审阅
> 当前切片：P0-2b 周报 resource_run 真实 SQL 契约

## 交付纪律

- 每次只处理一个可验证坏味道，不夹带技术栈升级或无关格式化；
- 所有生产改动先有能正确失败的测试；
- 每个切片结束前执行影响范围内完整回归、类型检查、lint 和真实运行态验收；
- `docs/project/CHANGELOG.md` 只记录已经验证的结果，本日志实时记录 RED/GREEN 过程；
- 根目录用户/既有未跟踪文件不纳入任何提交。

## Slice 1：百晓工坊历史缓存账户隔离

### 基线

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | 前端全量基线 | `npm run test -- --run` | 242 files / 2412 tests passed；0 failures |

### 设计与计划

- 设计：`docs/plans/2026-07-29-workshop-history-account-isolation-design.md`
- 实施：`docs/plans/2026-07-29-workshop-history-account-isolation-implementation.md`
- 采用显式 `userId + limit` 缓存键、可取消读取、Session 生命周期清理、组件晚到结果隔离和
  KeepAlive 账户级重建；后端继续以 JWT 为唯一授权事实源。

### TDD 证据

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | Vitest RED | `npx vitest run tests/api/workshopHistory.spec.ts tests/stores/session.spec.ts tests/components/workshop/ChatHistoryRail.spec.ts tests/pages/WorkshopPage.spec.ts src/test/App.test.ts` | 9 个预期失败：owner key、abort/clear、session cleanup、组件 owner 切换、KeepAlive 重建均未实现；其余 26 passed |
| 2026-07-29 | KeepAlive RED 校准 | `npx vitest run src/test/App.test.ts` | 初始 WorkshopPage 挂载 1 次，user 101→202 后仍为 1 次；1 expected failure / 3 passed |
| 2026-07-29 | Chromium RED | `BASE_URL=http://127.0.0.1:5173 npx playwright test e2e/workshopHistoryAccountIsolation.spec.ts --project=chromium --retries=0` | 预期失败：B 登录后 A 唯一历史 marker 仍持续存在，expected 0 / received 1 |
| 2026-07-29 | Focused GREEN | `npx vitest run tests/api/workshopHistory.spec.ts tests/stores/session.spec.ts tests/components/workshop/ChatHistoryRail.spec.ts tests/pages/WorkshopPage.spec.ts src/test/App.test.ts` | 5 files / 35 tests passed；owner key、清理取消、晚到结果、会话切换、组件重载与 KeepAlive 重建全部转绿 |
| 2026-07-29 | Chromium GREEN | `BASE_URL=http://127.0.0.1:5173 npx playwright test e2e/workshopHistoryAccountIsolation.spec.ts --project=chromium --retries=0` | 1 passed；同一 SPA/浏览器内 A 登出、B 登录后，A 唯一历史 marker 为 0 |
| 2026-07-29 | 前端全量 | `npm run test -- --run` | 242 files / 2418 tests passed；0 failures |
| 2026-07-29 | 类型检查 | `npm run typecheck` | 通过；`vue-tsc --noEmit` 0 errors |
| 2026-07-29 | ESLint | `npm run lint` | 通过；0 errors / 114 warnings，warnings 均不在本切片修改行 |
| 2026-07-29 | 格式检查 | `npx prettier --check --single-quote <11 个本切片前端文件>` | 新建 E2E 已格式化并单文件通过；10 个既有文件的 HEAD 版本本身均有 whole-file Prettier drift，为保持外科手术式修改未夹带全文件格式化 |

### Slice 1 收口

- 已完成：owner-aware 缓存、统一取消/清理、旧响应世代隔离、Session 生命周期绑定、
  ChatHistoryRail owner 切换、WorkshopPage 显式 owner、KeepAlive 账户级重建与真实浏览器回归。
- 未重跑：Backend、Graph、RAG 全量测试；本切片未修改其代码、数据库或 HTTP 合同。真实浏览器验收
  使用了本地 Backend / Graph / RAG 栈，覆盖了注册、登录、JWT 记历史与读取历史的实际链路。
- 剩余风险：Prettier whole-file 基线漂移仍存在；本切片没有扩大业务风险，但后续应单独安排纯格式化
  批次，避免与行为重构混在同一提交。
- 比赛演示影响：正向。公用浏览器换学生账号时不再显示上一位学生的工坊历史或保活页面状态；
  单账号工坊历史、JWT 授权与后端接口保持不变。

状态：Slice 1 完成，等待进入下一小步。

## Slice 2：Embedding Profile 跨语言契约

### 基线与事实复核

- 生产告警：`MemoryEmbeddingWriter expected=2048 actual=1024`，会跳过部分学习事件向量写入；
- 数据库事实：V175 已把记忆与上传语料向量列固定为 `halfvec(2048)`；
- 部署事实：开发与 `.env.example` 均声明 `PYT_RAG_EMBEDDER=zhipu`、
  `EMBEDDING_DIM=2048`，但显式 `hash` selector 仍硬走默认 1024；
- 边界事实：Pydantic `/embed` 已返回 `model/dim/vectors`，Java DTO 已接收元信息但客户端未执行校验。

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | RAG 定向基线 | `python -m pytest -q tests/test_zhipu_embedder.py tests/test_embed_route.py` | 7 passed |
| 2026-07-29 | Backend 定向基线 | `mvn --settings .mvn/settings.build.xml -Dtest=EmbeddingClientTest,MemoryEmbeddingWriterTest test` | 11 tests passed；0 failures / 0 errors |
| 2026-07-29 | RAG RED | `python -m pytest -q tests/test_zhipu_embedder.py` | 5 个预期失败 / 2 passed：hash 未服从配置维度，也未拒绝非法维度 |
| 2026-07-29 | Backend RED | `mvn --settings .mvn/settings.build.xml -Dtest=EmbeddingClientTest test` | 2 个预期失败 / 6 passed：声明维度不一致与非正维度仍被客户端接受 |
| 2026-07-29 | RAG Focused GREEN | `python -m pytest -q tests/test_zhipu_embedder.py tests/test_embed_route.py` | 12 passed |
| 2026-07-29 | Backend Focused GREEN | `mvn --settings .mvn/settings.build.xml -Dtest=EmbeddingClientTest,MemoryEmbeddingWriterTest test` | 13 tests passed；0 failures / 0 errors |
| 2026-07-29 | RAG 全量 | `python -m pytest -q` | 155 passed / 2 skipped |
| 2026-07-29 | RAG Ruff lint | `python -m ruff check .` | 通过；All checks passed |
| 2026-07-29 | Backend 首轮全量 | `mvn --settings .mvn/settings.build.xml test` | 1095 tests；3 failures / 2 errors。3 项 Stage07 旧测试仍 mock 已弃用的 `graphRestTemplate`；2 项 Testcontainers 因 Docker 随机宿主端口被占用而启动失败，均与本切片代码无调用关系 |
| 2026-07-29 | Stage07 根因 RED | `mvn --settings .mvn/settings.build.xml -Dtest=Stage07WorkshopAcceptanceIntegrationTest test` | 隔离复现 3 failures：生产已改用 `workshopRestTemplate`，测试仍绑定旧 client，mock 未收到请求而业务返回 502 |
| 2026-07-29 | Stage07 测试接线 GREEN | 同上 | 改为按 qualifier 绑定生产实际 `workshopRestTemplate` 后 4 passed；仅修测试夹具，不改生产行为 |
| 2026-07-29 | 容器失败隔离复跑 | `mvn --settings .mvn/settings.build.xml -Dtest=EvidenceMemoryServiceTest test`；同法运行 `RecommendationServiceTest` | 分别 3 passed、2 passed；两者都在真实 PostgreSQL/pgvector 空库应用 105 个迁移至 V190，首轮确认为随机端口环境碰撞 |
| 2026-07-29 | Backend 第二轮全量 | `mvn --settings .mvn/settings.build.xml test` | 1091 tests；0 failures / 3 errors。Stage07 已通过；3 errors 均发生于 pgvector 容器启动前，Docker 报随机宿主端口 `address already in use`，失败测试类与首轮不同 |
| 2026-07-29 | 第二轮容器失败隔离复跑 | `mvn --settings .mvn/settings.build.xml -Dtest=DecisionTraceControllerTest,LearningEvidenceGraphServiceTest,MemoryRetrievalSemanticIntegrationTest test` | 10 passed；3 个真实 PostgreSQL/pgvector 测试类均应用 V190 后通过，确认无业务/SQL 回归 |

设计：`docs/plans/2026-07-29-embedding-profile-contract-design.md`

实施：`docs/plans/2026-07-29-embedding-profile-contract-implementation.md`

格式与静态分析基线：

- `ruff format --check .` 报 21 个既有文件 whole-file drift；本切片两个 Python 文件的 HEAD
  版本本身也不满足当前 formatter，新增行无独立格式差异，因此未夹带全文件机械格式化；
- `mypy pythontopia_rag` 仅报告既有 `routes/query.py:38,50` 两项缺失返回类型，本切片文件 0 errors；
- Maven Spotless 插件解析被本机 TUN 与仓库 id 缓存不一致阻断；编译、聚焦测试及 Java diff check
  均已通过，门禁失败原因保留为环境证据。

### Slice 2 收口

- 已完成：显式 hash selector 服从部署维度、非法维度启动时 fail fast、zhipu/fallback
  复用同一解析规则；Java 在 HTTP 边界拒绝非正声明维度及声明/实际长度不一致的向量。
- 数据库与 provider 未变：V175 `halfvec(2048)`、上传语料与记忆写入的 2048 最终守卫均保留。
- 全量证据：RAG 155 passed / 2 skipped；Backend 两轮共覆盖全部套件，第二轮业务断言
  0 failures，3 个受本机 Docker 随机端口碰撞打断的测试类隔离复跑 10/10 通过。
- 附带修复：Stage07 旧验收夹具改绑生产实际 `workshopRestTemplate`，独立提交
  `a7e4b0e6`；仅恢复既有测试对 7 月 28 日专用 client 拆分后的覆盖，不改变产品行为。
- 比赛演示影响：正向。生产即使临时选择 hash fallback，也会按 2048 profile 生成可入库向量；
  违约 `/embed` 响应在进入记忆/上传语料存储前被隔离，主学习链路继续 fail-soft。
- 剩余风险：本切片尚未部署到 ECS，生产告警消失需在后续发布后复核；本机长套件的
  Testcontainers 随机端口碰撞和既有 formatter/mypy 基线漂移仍需单独治理。

状态：Slice 2 完成，下一小步为 P0-2b 周报 `resource_run` 真实 SQL 契约。

## Slice 3：周报 resource_run 真实 SQL 契约

### 基线与事实复核

- 生产告警：ECS 公网验收曾记录 `WeeklyReportService` 的 `resource_run` 查询
  `bad SQL grammar`，服务 catch 后返回空列表，周报其余内容可生成；
- schema 事实：V021 已定义查询使用的全部列，`created_at` 为 `TIMESTAMPTZ`，
  `(user_id, created_at DESC)` 索引存在，后续迁移未删除字段；
- 代码事实：SQL 字段、过滤、排序和限制均与 schema 对齐，唯一异常绑定是把 `Instant`
  直接作为无显式 SQL 类型的 `JdbcTemplate` vararg；
- 仓内惯例：其他 PostgreSQL 时间范围查询均先调用 `Timestamp.from(...)`；
- 覆盖缺口：既有 `WeeklyReportServiceTest` mock 了 `JdbcTemplate`，不会执行 JDBC 参数绑定、
  真实迁移或 `BIGINT[]` 映射。

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | 周报定向基线 | `mvn --settings .mvn/settings.build.xml -Dtest=WeeklyReportServiceTest,TwinWeeklyControllerTest test` | 6 tests passed；仅 mock/控制器合同，不覆盖真实 SQL |
| 2026-07-29 | 真库契约 RED | `mvn --settings .mvn/settings.build.xml -Dtest=WeeklyReportResourceRunIntegrationTest test` | 完整迁移 105 项至 V190 后 2 个预期失败：本人近期记录与 10 条上限均返回 0；服务复现 `PreparedStatementCallback; bad SQL grammar` |
| 2026-07-29 | 根因诊断 | `mvn --settings .mvn/settings.build.xml -Dtest=WeeklyReportResourceRunIntegrationTest#diagnosticExposesInstantBindingFailure test -e` | 预期 1 error；底层 `PSQLException` 明确为无法推断 `java.time.Instant` 的 SQL 类型，排除 schema 与 SQL 字段漂移 |
| 2026-07-29 | 真库契约 GREEN | `mvn --settings .mvn/settings.build.xml -Dtest=WeeklyReportResourceRunIntegrationTest test` | 2 passed；V190 真库上验证本人/时间窗隔离、倒序、`BIGINT[]` 计数与 10 条上限 |
| 2026-07-29 | 周报定向联跑 | `mvn --settings .mvn/settings.build.xml -Dtest=WeeklyReportResourceRunIntegrationTest,WeeklyReportServiceTest,TwinWeeklyControllerTest test` | 8 passed；真实 SQL、服务聚合与控制器合同全部通过 |
| 2026-07-29 | Backend 全量回归 | `mvn --settings .mvn/settings.build.xml test` | 1100 tests passed，0 failures / 0 errors / 0 skipped；构建成功并生成 JaCoCo 报告 |
| 2026-07-29 | Java 格式门禁 | `mvn --settings .mvn/settings.build.xml -DskipTests spotless:check` | 环境阻断：Maven 将已缓存插件归属到当前不可用的仓库 id，直连被 TUN 解析到 `198.18.0.95` 超时；7892 对 `curl` 可用，但 Maven 显式代理仍被同一 TUN 路径阻断；离线模式也因仓库 id 不匹配拒绝缓存 |
| 2026-07-29 | 提交前最终复验 | `mvn --settings .mvn/settings.build.xml -Dtest=WeeklyReportResourceRunIntegrationTest,WeeklyReportServiceTest,TwinWeeklyControllerTest test` | 8 passed，0 failures / 0 errors；完整迁移 105 项至 V190，`BUILD SUCCESS` |

设计：`docs/plans/2026-07-29-weekly-report-resource-run-sql-contract-design.md`

实施：`docs/plans/2026-07-29-weekly-report-resource-run-sql-contract-implementation.md`

### Slice 3 收口

- 已完成：周报七天窗口以 `Timestamp.from(weekStart)` 绑定 `TIMESTAMPTZ`；其余 SQL、
  DTO、API、时间窗、排序、限制和 fail-soft 边界保持不变。
- 真库契约：从空 PostgreSQL 执行 105 项迁移到 V190，验证本人近期记录、跨账号与七天外隔离、
  `BIGINT[]` 证据计数、倒序和 10 条上限。
- 全量证据：Backend 1100 passed，0 failures / 0 errors / 0 skipped；退出期 Surefire
  等待残留 Spring 调度线程并输出数据库已关闭告警，但最终结果为 `BUILD SUCCESS`。
- 静态检查：`git diff --check` 通过；Spotless 因上述本机 Maven 仓库/TUN 环境问题未执行，
  未以格式化命令扩大修改面。
- 比赛演示影响：正向。周报不再因 JDBC 时间参数类型不确定而静默漏掉资源生成轨迹，
  学生近七天的 Agent 运行摘要恢复为真实数据库事实。
- 剩余风险：本切片尚未部署 ECS；生产 `bad SQL grammar` 告警消失和真实历史数据展示
  仍需在后续发布验收中复核。

状态：Slice 3 完成；复核附件“建议实施顺序”后，下一小步在下节校正为 AG-UI 契约。

## Slice 4：AG-UI STATE_DELTA RFC 6902 契约

### 基线与事实复核

- 附件实施顺序明确要求先完成 OpenAPI/AG-UI 契约与统一 SSE，再进入工坊三阶段事务；
  因此本切片优先于原先按方向编号选择的事务切片；
- AG-UI 当前官方合同要求 `STATE_DELTA.delta` 为 RFC 6902 JSON Patch 操作数组，
  `RUN_STARTED` 则要求 `threadId/runId`；
- 当前 Graph 唯一生产 `STATE_DELTA` 是 orchestrator 的完整 `plan_steps` 进度，
  仍发送 `{"delta":{"plan_steps":[]}}`；
- 当前前端把 `delta` 声明为对象并浅合并，无法执行嵌套/删除等 JSON Patch 语义；
- 本切片只修 `STATE_DELTA`；生命周期 id 与统一 transport 分别留给后续小切片；
- npm 通过 7892 实测 `fast-json-patch@3.1.1` 为 MIT、解包 159025 bytes，采用成熟实现，
  不自写 RFC 6902 引擎。

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | 前端定向基线 | `npm run test -- tests/composables/useSseStream.spec.ts` | 1 file / 5 tests passed；存在既有 Pyodide source map 缺失告警，不影响结果 |
| 2026-07-29 | Graph 命令环境 | `python -m pytest -q tests/test_sse_agui.py tests/test_orchestrator_route.py` | 未执行测试：当前工作树无 `python` 别名，改用仓库可用的 `python3` |
| 2026-07-29 | Graph 定向基线 | `python3 -m pytest -q tests/test_sse_agui.py tests/test_orchestrator_route.py` | 27 passed |
| 2026-07-29 | JSON Patch 依赖复核 | `npm view fast-json-patch version license dist.unpackedSize --proxy http://127.0.0.1:7892` | 3.1.1 / MIT / 159025 bytes |

设计：`docs/plans/2026-07-29-agui-state-delta-json-patch-design.md`

实施：`docs/plans/2026-07-29-agui-state-delta-json-patch-implementation.md`

状态：事实与方案已冻结，下一步建立 Graph 与前端双端 RED。
