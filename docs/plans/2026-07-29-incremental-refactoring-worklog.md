# Pythontopia 小步重构工作日志

> 开始时间：2026-07-29
> 当前分支：`refactor/workshop-history-account-isolation`
> 总体输入：用户提供的八方向重构审阅
> 当前切片：P1-4c GraphStreamTransport 公共包归属

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
| 2026-07-29 | Graph 契约 RED | `python3 -m pytest -q tests/test_sse_agui.py tests/test_orchestrator_route.py` | 1 expected failure / 26 passed；路由实流中的 `STATE_DELTA.delta` 仍为对象而非操作数组 |
| 2026-07-29 | 前端执行 RED | `npm run test -- tests/composables/useSseStream.spec.ts` | 1 expected failure / 5 passed；RFC 6902 数组被浅合并成 `"0"/"1"/"2"` 状态键，嵌套替换与删除均未执行 |
| 2026-07-29 | Graph 定向 GREEN | `python3 -m pytest -q tests/test_sse_agui.py tests/test_orchestrator_route.py` | 27 passed；helper 与路由实流均输出 RFC 6902 操作数组 |
| 2026-07-29 | 前端定向 GREEN | `npm run test -- tests/composables/useSseStream.spec.ts` | 6 passed；replace/remove/add 与旧对象载荷兼容均通过 |
| 2026-07-29 | 前端协议/工坊联跑 | `npx vitest run tests/composables/useSseStream.spec.ts tests/components/workshop/WorkshopChat.spec.ts --coverage.enabled=false` | 2 files / 51 tests passed；计划卡继续消费 `state.plan_steps` |
| 2026-07-29 | TypeScript strict | `npm run typecheck` | passed |
| 2026-07-29 | Graph 全量首轮 | `python3 -m pytest -q` | 1672 passed / 3 skipped，7 failures；均为 `test_workshop_route_defects.py` 导入仓库 sibling fixture 时命中第三方 `tests` 包 |
| 2026-07-29 | Graph 导入根因 | `python3 -c "import tests; print(tests.__file__)"` | 指向 `/home/cypress/.local/lib/python3.10/site-packages/tests/__init__.py`；仓库 `tests/` 缺显式 package 边界 |
| 2026-07-29 | Graph 夹具修复 | `python3 -m pytest -q tests/test_workshop_route_defects.py` | 新增 `tests/__init__.py` 后 31 passed；独立提交 `faa6d59f`，无生产行为修改 |
| 2026-07-29 | Graph 全量复验 | `python3 -m pytest -q` | 1679 passed / 3 skipped；仅 1 条既有 FastAPI 422 常量弃用告警 |
| 2026-07-29 | 前端全量回归 | `npm run test` | 242 files / 2419 tests passed |
| 2026-07-29 | 前端生产构建 | `npm run build` | TypeScript strict + Vite 4068 modules 构建成功；同步脚本生成的 manifest 时间戳已恢复，未夹带 |
| 2026-07-29 | 前端 lint | `npm run lint` | exit 0，0 errors / 114 warnings；warning 均为既有 Vue 模板风格，无本切片 TS 命中 |
| 2026-07-29 | 提交前最终复验 | Graph 定向 27 项 + 前端协议/工坊 51 项 | 27 passed；2 files / 51 tests passed |
| 2026-07-29 | 生产依赖审计 | `npm audit --omit=dev --audit-level=high --registry https://registry.npmjs.org --proxy http://127.0.0.1:7892` | 新增 `fast-json-patch` 无告警；报告既有 Axios/form-data/PostCSS 3 high、DOMPurify 1 moderate，按独立依赖升级列车处理 |

设计：`docs/plans/2026-07-29-agui-state-delta-json-patch-design.md`

实施：`docs/plans/2026-07-29-agui-state-delta-json-patch-implementation.md`

### 格式与静态分析基线

- 变更 Python 文件 `ruff check` 全部通过；Graph 全量 `ruff check .` 仅报告既有
  `tests/test_agentic_ab.py:5` 未使用 `pytest`；
- `ruff format --check .` 报 172 个既有文件 drift；本切片五个 Python 文件中
  `sse_agui.py`、两项新/改测试和测试包文件已符合 formatter，`orchestrator_graph.py` 与
  `test_orchestrator_route.py` 的 HEAD 版本本身存在 unrelated whole-file drift，未扩大修改面；
- 定向 Prettier 对三个既有 TS 文件仍报告 whole-file drift，HEAD 输入同样失败；新增行已按
  single-quote 风格局部对齐，package JSON 通过，ESLint 定向与全量均为 0 errors；
- `git diff --check` 通过。

### Slice 4 收口

- 已完成：Graph canonical `STATE_DELTA` 使用 RFC 6902 操作数组；前端类型化解析并用成熟库
  安全执行 patch，旧对象载荷只保留滚动发布兼容。
- 行为边界不变：orchestrator 仍发送完整 `plan_steps` 当前值，组件继续读取
  `state.plan_steps`；Java 中转、数据库、API、鉴权、运行生命周期和 SSE transport 未改。
- 全量证据：Graph 1679 passed / 3 skipped；前端 242 files / 2419 tests、生产构建、
  TypeScript strict 和 ESLint 通过。
- 比赛演示影响：正向。复合任务计划卡继续实时点亮，同时状态流可被标准 AG-UI 客户端执行，
  不再只是“事件名看起来兼容”。
- 剩余风险：本切片未部署 ECS；`RUN_STARTED/RUN_FINISHED` 的 run/thread id 仍未全端点对齐，
  统一超时、心跳、取消与单终态 transport 也在后续切片；npm audit 的 4 项既有依赖告警
  不属于本切片，需在独立依赖升级发布列车中逐项验证修复。

状态：Slice 4 完成，下一小步为 AG-UI 生命周期 `threadId/runId` 契约。

## Slice 5：AG-UI 生命周期身份契约

### 基线与设计决策

- 官方 `RUN_STARTED/RUN_FINISHED` 合同都要求 `threadId/runId`；
- 当前主 orchestrator 的开始事件把 `runId` 固定为 `orchestrator:{session_id}`，
  同一会话多轮重复；结束事件只有 `result`，前端又会丢弃已有身份字段；
- `threadId` 继续使用长期会话的 `session_id`；`runId` 使用路由请求级 UUID，避免依赖
  图内部晚于开始事件才能确定的 turn 计数，也避免并发竞态；
- 本切片只修改主 `/agents/orchestrator/turn/stream` 成功生命周期；其他端点继续走
  无上下文兼容分支，错误终态和统一 transport 留给后续切片；
- 前端内部类型为兼容历史端点保留可选字段，但 canonical 路由与解析测试强制完整 wire contract。

设计：`docs/plans/2026-07-29-agui-lifecycle-identity-design.md`

实施：`docs/plans/2026-07-29-agui-lifecycle-identity-implementation.md`

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | 身份链路复核 | `rg` + 定向源码检查 | 确认开始事件 run 身份按 session 重复、结束事件丢失身份、前端 parser 丢弃身份 |
| 2026-07-29 | Graph 定向基线 | `python3 -m pytest -q tests/test_sse_agui.py tests/test_orchestrator_route.py` | 27 passed |
| 2026-07-29 | 前端定向基线 | `npx vitest run tests/composables/useSseStream.spec.ts --coverage.enabled=false` | 1 file / 6 tests passed |
| 2026-07-29 | Graph 生命周期 RED | 同 Graph 定向命令 | 4 expected failures / 26 passed；helper 不接受生命周期上下文，主流 run 身份按 session 重复，成功终态缺身份 |
| 2026-07-29 | 前端身份保留 RED | 同前端定向命令 | 1 expected failure / 5 passed；canonical `RUN_STARTED` 的 `threadId/runId` 被 parser 丢弃 |
| 2026-07-29 | Graph 定向 GREEN | 同 Graph 定向命令 | 32 passed；含成对身份、同 session 两请求 run 唯一和 partial identity fail-fast |
| 2026-07-29 | 前端定向 GREEN | 同前端定向命令 | 1 file / 6 tests passed；canonical 生命周期身份完整保留 |
| 2026-07-29 | 前端受影响联跑 | `npx vitest run tests/composables/useSseStream.spec.ts tests/stores/loopSlice.spec.ts tests/composables/useToolLoop.spec.ts --coverage.enabled=false` | 3 files / 23 tests passed；旧内部事件构造与 loop 消费兼容 |
| 2026-07-29 | TypeScript strict | `npm run typecheck` | passed |
| 2026-07-29 | Graph 变更 lint | `ruff check` 四个变更 Python 文件 | passed |
| 2026-07-29 | Graph 全量回归 | `python3 -m pytest -q` | 1684 passed / 3 skipped；1 条既有 FastAPI 422 常量弃用告警 |
| 2026-07-29 | 前端全量首轮 | `npm run test` | 2418 passed / 1 failed；与本切片无代码交集的 TrainingPanel 草稿断言被上一用例 500ms 定时器晚到回写污染 |
| 2026-07-29 | TrainingPanel 根因复跑 | `npx vitest run tests/components/TrainingPanel.spec.ts --coverage.enabled=false` | 12 passed；单文件耗时短于定时器窗口，结合失败值 `print(0)` 锁定跨用例 debounce 污染 |
| 2026-07-29 | TrainingPanel 夹具隔离 | 同 TrainingPanel 定向命令 | fake timer 按用例创建/清理后 12 passed；独立提交 `e3be9431`，生产代码零改动 |
| 2026-07-29 | 前端全量复验 | `npm run test` | 242 files / 2419 tests passed |
| 2026-07-29 | 前端 lint | `npm run lint` | exit 0，0 errors / 114 个既有 Vue 模板 warning；本切片 TS 无命中 |
| 2026-07-29 | 前端生产构建 | `npm run build` | TypeScript strict + Vite 4068 modules 构建成功；生成 manifest 仅时间戳变化并已恢复，未夹带 |
| 2026-07-29 | 提交前最终复验 | Graph 定向 32 项 + 前端受影响 23 项 + `git diff --check` | 32 passed；3 files / 23 tests passed；diff check passed |

### 格式与静态分析边界

- Graph 四个变更 Python 文件的 `ruff check` 全部通过；`ruff format --check` 只指出
  `orchestrator.py` 与 `test_orchestrator_route.py` 各一处本切片前已存在的 whole-file drift，
  用 `git show HEAD:<path> | ruff format --check -` 复核 HEAD 同样失败；新增代码未引入格式差异；
- 前端 TypeScript strict、ESLint 和生产构建通过；114 个 lint warning 与 Slice 4 基线一致，
  均在既有 Vue 文件，变更的 `agui.ts` 与测试无命中；
- `git diff --check` 作为提交前最终门禁执行，不用 formatter 扩大无关修改面。

### Slice 5 收口

- 已完成：主 orchestrator 每次流请求生成独立 `orchestrator:<uuid>` run 身份，长期
  `threadId=session_id`；成功开始/终态携带同一组身份，partial identity 在 helper 边界 fail fast；
- 兼容边界：其他 Graph 端点无上下文时维持原 `RUN_FINISHED.result` 形状；前端 canonical 事件
  保留身份，历史 `result` 与缺身份旧端点继续可消费；
- 全量证据：Graph 1684 passed / 3 skipped；前端 242 files / 2419 tests、生产构建、
  TypeScript strict 与 ESLint（0 errors）通过；
- 比赛演示影响：正向。同一导学会话的连续/并发回合现在可按 run 精确追踪，不再互相覆盖；
- 剩余风险：本切片未部署 ECS；`RUN_ERROR`、tool-loop/tutor/workflow 等其他流端点尚未统一成对身份，
  心跳、deadline、取消、序号和恰好一个终态仍属于后续统一 transport 切片。

状态：Slice 5 完成；下一小步继续附件顺序，审计并收敛 Java 两套 SSE 中转的最小公共 transport 边界。

## Slice 6：Java SSE 公共帧解码合同

### 基线与设计决策

- `GraphSseConsumer`（工坊/画像/TutorWorkflow）与 `GraphStreamRelay`（Tutor/Orchestrator）
  各自复制行解析循环，后者已有更强的超时、断连、截断和单终态语义；
- 两套实现都把多行 `data:` 直接拼接，违反 WHATWG 以 LF 连接的合同；旧 consumer 还会把
  EOF 前未以空行完成的半帧上送，Relay 则会丢弃并补中断终态；
- 本切片先建立 `common.sse.SseFrameDecoder`，让两套路径共享 wire 解释；HTTP client、超时、
  fallback 与业务回调继续由上层负责，后续再逐调用方迁移到统一 transport；
- 采用 JDK 自有类型，不引入新依赖，不换 WebFlux，不做重连或开放式兼容层。

设计：`docs/plans/2026-07-29-sse-frame-decoder-design.md`

实施：`docs/plans/2026-07-29-sse-frame-decoder-implementation.md`

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | Java SSE 调用链审计 | `rg` + 两套实现/调用方/失败测试源码检查 | 确认 2 套 parser、5 条 Graph 调用链及 EOF/多行 data 行为分叉 |
| 2026-07-29 | WHATWG 合同复核 | HTML Living Standard §9.2.5–9.2.6 | 官方要求 data 行追加 LF、注释忽略、空行分发、EOF pending data 丢弃 |
| 2026-07-29 | Relay 定向基线 | `mvn -q -Dtest='OrchestratorGraphClientTest,OrchestratorGraphClientFailureTest,TutorGraphClientSseTest,TutorGraphClientFailureTest' test` | 4 classes / 15 tests passed；正常、HTTP/连接错误、读超时、截断和客户端断开合同均绿 |
| 2026-07-29 | Consumer 真流 RED | `mvn -q -Dtest=GraphSseConsumerTest test` | 1 expected failure；实际错误输出 `firstsecond` 且额外分发半截 result，精确命中两个 parser 缺陷 |
| 2026-07-29 | Decoder 类合同 RED | `mvn -q -Dtest=SseFrameDecoderTest test` | expected test-compile failure；公共 decoder 尚不存在 |
| 2026-07-29 | 公共 decoder 定向 GREEN | 设计实施计划 Task 3 的 6 类定向命令 | 6 classes / 20 tests passed；新增 5 项标准帧合同 + 既有 Relay 15 项故障合同 |
| 2026-07-29 | Relay 多行旁路复核 | `mvn -q -Dtest='GraphSseConsumerTest,SseFrameDecoderTest,OrchestratorGraphClientTest' test` | 3 classes / 9 tests passed；peek 收到标准 `{"text":\n"hi"}`，SseEmitter 仍按 wire data 行正确编码 |
| 2026-07-29 | 变更文件 Spotless | `mvn -q -DspotlessFiles='<6 changed java files>' spotless:check` | passed |
| 2026-07-29 | 全仓 Spotless 基线 | `mvn -q spotless:check` | 既有 570 个 Java 文件格式漂移；未执行 apply，避免扩大修改面 |
| 2026-07-29 | Backend 全量首轮 | `mvn test` | 1090 tests；0 failures / 6 errors。6 项均为 `pgvector` Testcontainers 启动前随机宿主端口 `address already in use`，无业务断言失败 |
| 2026-07-29 | 首轮容器错误隔离复跑 | `mvn -q -Dtest='CoursePackServiceIntegrationTest,DecisionTraceControllerTest,EvidenceMemoryServiceTest,LearningEvidenceGraphServiceTest,MemoryRetrievalSemanticIntegrationTest,RecommendationServiceTest' test` | 6 classes / 18 tests passed；真实 PostgreSQL/pgvector 与 V190 迁移均通过 |
| 2026-07-29 | Backend 全量复验 | `mvn test` | 1090 tests；0 failures / 7 errors。相同 6 项容器随机端口冲突再次出现，另有 `LongTermClassroomEffectSimulationTest` 因数据库容器被连带中断而连接失败 |
| 2026-07-29 | 连带错误隔离复跑 | `mvn -q -Dtest=LongTermClassroomEffectSimulationTest test` | 1 test passed；独立 pgvector 容器、完整 V190 迁移与长期课堂模拟全部通过 |

### Slice 6 收口

- 已完成：新增无日志、无业务策略的 `SseFrameDecoder`；旧 consumer 与强 Relay 共用同一
  WHATWG 帧解释，删除两份重复 line parser；HTTP client、超时、fallback、emitter 和终态策略不变。
- 协议边界：首行 BOM、注释/未知字段、默认 `message`、显式空 data、多行 LF 合并、空行分发、
  handler 提前停止与 EOF 半帧丢弃均有合同测试；旧 consumer 不再把截断 JSON 上送业务回调。
- 全量证据：Backend 全量命令执行两次，均为业务断言 0 failures，但被本机 Docker 随机宿主端口
  分配冲突阻断为 6/7 个环境 errors；所有受影响测试类随后隔离复跑 19/19 通过，不能据此宣称获得
  一次单命令全绿，环境限制已如实保留。
- 格式证据：6 个变更 Java 文件 Spotless 通过；全仓检查仍有 570 个既有文件 drift，本切片不夹带
  全仓机械格式化。
- 比赛演示影响：正向。多行 AG-UI/SSE 载荷保留标准换行，截断尾帧不会被当作完整结果继续落库或
  渲染；既有端点、正常单行载荷及错误终态保持兼容。
- 未验证与风险：尚未部署 ECS、未做公网长流验收；`GraphSseConsumer` 三个调用方仍缺少强 Relay
  的统一读超时、客户端取消和单终态保障，下一切片应逐调用方迁移而非一次性替换全部。

状态：Slice 6 完成；下一小步为 P1-4b 旧 Graph SSE 调用方向统一 transport 的首个迁移切片。

## Slice 7：TutorWorkflow SSE 强中继迁移

### 基线与设计决策

- 三个旧 consumer 调用方中优先 TutorWorkflow：同包可直接复用 Relay、无同步 fallback，业务只做
  原样透传与终态投影；Workshop/Profile 的额外变形和状态副作用留给后续独立切片；
- 当前旧路径在无终态 EOF 时静默完成，客户端断开后继续读上游，异常用 `completeWithError` 裸断流；
- Python TutorWorkflow 路由已有 10 秒注释心跳，生产 read inactivity timeout 对齐现有 180 秒 emitter；
- 本切片不新增抽象，只迁移一个调用方并复用已经测试的 `GraphStreamRelay`。

设计：`docs/plans/2026-07-29-tutor-workflow-sse-transport-design.md`

实施：`docs/plans/2026-07-29-tutor-workflow-sse-transport-implementation.md`

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | 旧调用方与 Graph 路由审计 | `rg` + Java/Python 定向源码检查 | 确认 TutorWorkflow 是最小迁移面；Graph 每 10 秒 ping，终态为 `RUN_FINISHED/RUN_ERROR` |
| 2026-07-29 | TutorWorkflow/共享 Relay 基线 | `mvn -q -Dtest='TutorWorkflowServiceTest,TutorGraphClientSseTest,TutorGraphClientFailureTest' test` | 3 classes / 16 tests passed；投影、正常透传及 Relay 截断/超时/断开合同基线为绿 |
| 2026-07-29 | 无终态 EOF RED | `mvn -q -Dtest=TutorWorkflowGraphClientTest test` | 1 expected failure；trace 已到达，但旧客户端静默 complete，预期唯一中断 error 实际为 0 |
| 2026-07-29 | 可控读超时 RED | 同目标测试命令 | expected test-compile failure；旧客户端没有可注入 connect/read timeout 接线 |
| 2026-07-29 | 目标客户端 GREEN | 同目标测试命令 | 1 class / 4 tests passed；成功 RUN_FINISHED、上游 RUN_ERROR、无终态 EOF 与 stalled timeout 合同均绿 |
| 2026-07-29 | 目标与共享 Relay 联跑 | `mvn -q -Dtest='TutorWorkflowGraphClientTest,TutorWorkflowServiceTest,TutorGraphClientSseTest,TutorGraphClientFailureTest,OrchestratorGraphClientTest,OrchestratorGraphClientFailureTest' test` | 6 classes / 30 tests passed；投影、认证、透传、截断、超时、连接失败和客户端断开守恒 |
| 2026-07-29 | Spring 全上下文 | `mvn -q -Dtest=PythontopiaApplicationTests test` | 1 test passed；`GraphHttpClientProperties` 新构造注入成功 |
| 2026-07-29 | 变更文件 Spotless | `mvn -q -DspotlessFiles='<2 changed java files>' spotless:check` | passed |
| 2026-07-29 | Backend 全量 | `mvn test` | 1094 tests；0 failures / 7 errors。6 项为 pgvector 随机宿主端口启动冲突，1 项课堂模拟因其数据库连接被连带中断；Slice 7 新测试全部通过 |
| 2026-07-29 | 端口冲突根因 | `ss -tanp` + `sysctl net.ipv4.ip_local_port_range` | 失败端口位于 44620–48715 临时区间，且当时正是 host→container:5432 的 TIME_WAIT；Docker 29 从同一区间分配下一映射而自碰撞 |
| 2026-07-29 | 全量错误类新鲜隔离复跑 | 等待上一 PostgreSQL TIME_WAIT 自然释放后，每类独立执行 `mvn -q -Dtest='<class>' test` | 7 classes / 19 tests passed；完整 V190 迁移、课堂模拟与六个真库合同全部通过，未修改系统端口或重启 Docker |

### Slice 7 收口

- 已完成：TutorWorkflow 从旧 consumer 迁到既有强 Relay；连接超时复用 Graph HTTP 配置，读不活动
  上限对齐 180 秒 emitter，Python 10 秒注释心跳保持正常长生成活跃。
- 成功合同不变：请求 body、`X-Internal-Token`、事件名与 data 原样透传；`result/RUN_FINISHED`
  仍以原始 payload 恰好一次触发投影 callback。
- 故障合同收紧：连接/HTTP/超时/无终态 EOF 都以唯一净化 error 收尾；部分 trace 保留，客户端断开
  停止上游，所有路径只 complete 一次且不再裸 `completeWithError`。
- 全量证据：目标与共享 Relay 30/30、Spring 上下文 1/1、变更文件 Spotless 通过；Backend 全量
  1094 项业务断言 0 failures，但 7 个 Docker/Testcontainers 环境 errors 阻止单命令纯绿，全部错误类
  已按内核 TIME_WAIT 证据安全串行复跑 19/19 通过。
- 比赛演示影响：正向。导学工作流 Graph 中断不再被 UI 当成成功结束，学生能收到可重试错误；正常卡片
  生成、AG-UI 终态和 Java 投影落库形状不变。
- 未验证与风险：尚未部署 ECS、未做真实浏览器/公网长流验收；Workshop 与 Profile 两个旧 consumer
  调用方仍未获得强 Relay 的取消和单终态语义，且首包独立 deadline、序号仍未统一。

状态：Slice 7 完成；下一小步为 P1-4c Workshop/Profile 中风险较低调用方的独立 transport 迁移设计。

## Slice 8：GraphStreamTransport 公共包归属

### 基线与设计决策

- 强 Relay 已服务 Tutor、Orchestrator、TutorWorkflow，内部不依赖 Tutor DTO/业务，但历史上仍放在
  `tutor` 包；继续迁移 Workshop/Profile 会制造反向模块依赖；
- 本切片按附件目标改名为 `common.sse.GraphStreamTransport`，只做原子符号/包迁移，不改行为；
- 不保留旧类兼容 wrapper，避免制造第二入口；剩余调用方迁移留在后续业务切片。

设计：`docs/plans/2026-07-29-graph-stream-transport-package-design.md`

实施：`docs/plans/2026-07-29-graph-stream-transport-package-implementation.md`

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | 包依赖审计 | `rg GraphStreamRelay` + 三个生产调用方源码检查 | 仅 1 个实现、3 个调用方；实现无 Tutor 业务依赖，适合提升到 common |
| 2026-07-29 | 迁移前守恒基线 | Slice 7 最终 6 类定向命令 | 6 classes / 30 tests passed；成功、故障、断开、投影合同均绿 |
| 2026-07-29 | 纯机械类型迁移 | `tutor.GraphStreamRelay` → `common.sse.GraphStreamTransport`，切换 3 个调用方 | 旧符号在 `backend/src` 归零；公共实现 1 处、生产调用方 3 处 |
| 2026-07-29 | 语义 diff 守卫 | HEAD 旧实现经 package/class 替换后与新文件 `diff -u` | 仅类级 Javadoc 与 `{@link GraphSseConsumer}` 链接变化，常量/方法/控制流无差异 |
| 2026-07-29 | 迁移后守恒回归 | Slice 7 同一 6 类定向命令 | 6 classes / 30 tests passed；日志来源切到 `common.sse.GraphStreamTransport` |
| 2026-07-29 | Spring 全上下文 | `mvn -q -Dtest=PythontopiaApplicationTests test` | 1 test passed；三个客户端构造链正常 |
| 2026-07-29 | 变更文件 Spotless | `mvn -q -DspotlessFiles='<4 changed java files>' spotless:check` | passed |
| 2026-07-29 | Backend 全量 | `mvn test` | 1094 tests；0 failures / 6 errors。6 项均为已确认的 pgvector 临时端口 TIME_WAIT 自碰撞；课堂模拟本轮直接通过 |
| 2026-07-29 | 全量错误类新鲜隔离复跑 | 等待上一 PostgreSQL TIME_WAIT 自然释放后，每类独立执行 `mvn -q -Dtest='<class>' test` | 6 classes / 18 tests passed；完整 V190 真库合同均通过 |

### Slice 8 收口

- 已完成：历史 `tutor.GraphStreamRelay` 原子提升为唯一 `common.sse.GraphStreamTransport`；Tutor、
  TutorWorkflow、Orchestrator 三个调用方全部切换，旧符号在源码中归零且无兼容 wrapper。
- 行为守恒：构造参数、HTTP 打开、decoder、超时、错误码、终态识别、callback 与资源收口控制流均不变；
  语义 diff 仅剩公共职责 Javadoc 与类名/package。
- 验证：迁移前后同一 30 项合同均绿，Spring 上下文 1/1、4 文件 Spotless 通过；Backend 全量
  1094 项业务断言 0 failures，6 个 Docker 环境 errors 新鲜隔离复跑 18/18 通过。
- 比赛演示影响：中性。运行行为不变，但统一 transport 现在位于正确公共边界，为 Workshop/Profile
  接入取消、超时和单终态铺平路径，不再要求业务模块依赖 tutor 包。
- 未验证与风险：尚未部署 ECS；本切片未迁剩余两个旧 consumer，也未新增首包 deadline、序号或重连。

状态：Slice 8 完成；下一小步为 P1-4d Profile/Workshop 的业务适配迁移设计。

## Slice 9：Profile SSE 显式 recovery 迁移

### 基线与设计决策

- Python Profile 流已发 canonical `RUN_FINISHED.result`，Java 旧路径只识别 legacy `result`，存在成功
  访谈漏投影；
- Profile/Workshop 都需同步 fallback，公共 transport 必须先支持“业务返回替代终态”，否则会 error +
  result 双终态；
- recovery 只返回白名单终态，由 transport 统一 send/complete；Profile 同步 `sendTurn` 负责既有副作用，
  adapter 不重复投影；
- 前端当前仍使用同步画像 API，先迁 Profile 可降低演示主链路风险并固定公共 recovery 合同。

设计：`docs/plans/2026-07-29-profile-sse-transport-recovery-design.md`

实施：`docs/plans/2026-07-29-profile-sse-transport-recovery-implementation.md`

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | Profile/Workshop fallback 审计 | Java/Python/前端定向源码检查 | 确认 canonical Profile wrapper 漏投影、两业务 fallback 与 transport 自动 error 的冲突；Web 当前不用 Profile SSE |
| 2026-07-29 | Profile 既有单测基线 | `mvn -q -Dtest='ProfileServiceFailureTest,InterviewDimensionMirrorTest' test` | 2 classes / 8 tests passed；同步错误映射与 8 维镜像为绿 |
| 2026-07-29 | 公共 transport 基线 | Slice 8 最终共享合同命令 | 6 classes / 30 tests passed |

状态：Slice 9 设计完成，下一步建立 canonical RUN_FINISHED 漏投影真流 RED。
