# Pythontopia Web 系统审计与修复 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Close the confirmed Web security leaks and finish evidence-based verification of the existing AutoGen tutor, expedition, learner twin, and admin flows without duplicating architecture.

**Architecture:** Keep Spring APIs, PostgreSQL state, fixed-stage AutoGen, DeepSeek-only model routing, server-owned learning evidence, and existing Vue/Phaser components. Make only narrow fixes in current modules; use existing deployment limits, migrations, test helpers, and UI primitives.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL/Flyway, Python 3.11+, FastAPI/LangGraph/AutoGen, Vue 3/TypeScript, Phaser 3, Vitest, Playwright.

## 总任务清单

本节是当前交付状态的速查清单；各任务详情和验证证据保留在下方。

- [x] Web 安全基线：凭据通配 CORS、媒体错误脱敏、公网注册/登录/加入码限流契约。
- [x] 课堂加入码账号限速：30 次/10 分钟；Tutor 工作流错误对学生脱敏。
- [x] Tutor 用量锁按用户账号与上海自然日生效；本地开发明确关闭应用日限额，部署默认开启。
- [x] DeepSeek 文本与合成 PNG 图片输入均有历史真实调用成功记录，Tutor 输出为文本；Graph 所有生成路由复用 DeepSeek host/model 白名单。RAG 向量使用本地 hash/BGE，讯飞 embedding 仅是不可选的未实现占位；TTS、ASR、旧讯飞文生图/图片理解硬关闭。较新的请求另出现 Tutor relay 超时和 DeepSeek 503，当前不重复探针，稳定性仍未验证。
- [x] 无尽远征采用 Phaser Boss 场景，包含判题受击/失败体力反馈及登记过的 CC0 音效；真实三阶段通关已有浏览器记录。
- [x] AI 编程题复用现有审核与共享题库；KC-010 已真实判题通过，KC-004、KC-002、KC-006、KC-011 均由正常学生补题路径生成并经自动审查入库。KC-002 另由第二名学生从已批准题库取题，服务端 3/3 判题通过；KC-006 由另一名学生从共享题库取题并经服务端判题通过，临时账号、学习事件与掌握度事件已清理。KC-011 目前只有自动批准记录，尚无正式学生判题或掌握度证据。KC-002/004/006 的普通主线题 `kind=NULL`，不满足远征战斗题型。
- [x] Twin P0 状态聚合、证据可信度、周报真实增量、共享 AdaptivePracticePolicy 和带证据/下一步的学习分身页面已有代码与定向验证。
- [x] 首页与学习分身对 Twin 推荐使用同一目的地规则；未评估 KC 返回主线、到期复习进入复习、已评估 KC 进入远征。
- [x] 世界 HUD 中未评估 Twin 推荐的真实浏览器点击：关闭可选探针后鼠标展开目标面板并到达主线地图，0/65 已评估、掌握证据未改变、浏览器错误为 0；修复 `role="button"` 标题被拖动手势拦截的问题。
- [x] 远征对服务端不可判定的无效题提供重新出题操作，不扣体力也不提交结算；加入 HUD 拖拽指针回归后，5 个受影响前端测试文件共 76 项、typecheck、ESLint、diff 检查通过。
- [x] AutoGen 对照审查确认 Pythontopia 已有固定阶段 Tutor；保留现有 SSE、授权、附件和 V199/V203/V208 run 检查点契约，不移植 Alethicode 的 V124/thread dispatcher。
- [x] 当前 HEAD 的只读安全复核未发现确认的 P0/P1 代码缺陷；认证、主要 IDOR、判题 fail-closed 与 CORS 有实现和测试落点。生产 Piston 若无私网 ACL 或校验 Bearer 的代理，仍有未授权执行风险；本地无法代表生产确认。
- [x] 管理台共享表格表头/圆角/分页已按 Alethicode 样式收敛；保留手动跳页和现有数据字段。
- [x] 管理员真实浏览器用户管理流程：登录、搜索、停用/恢复、禁用账号登录拒绝；临时账号已清理。
- [x] 运维概览将五个已知后端动作键映射为中文建议，原始键保留在提示文本中。
- [x] KC-004 根知识点 AI 补题：从正常主线练习按钮发起请求，通过 Piston 自测和现有质量审查后写入共享题库为 `APPROVED/AUTOMATED`。实际请求 6 个 DeepSeek completions（前 5 个候选自测输出不符，第 6 个通过）；未提交答案或新增掌握证据。
- [x] KC-002 主线 AI 补题：从第 1 章第 3 节正常学习页发起 1 次生成请求，题目 `APPROVED/AUTOMATED`（审核 ID 83）；第二名学生从共享题库取题并服务端判题 3/3。临时答题账号及其掌握事件已清理，审核请求者因审计外键保留在本地数据库。
- [ ] 远征：用有效先修学习账号从服务端活动 run 刷新并恢复。当前根 KC 审核库题型为 KC-001 `debug`、KC-002/004/006 `kind=NULL`；均不适配普通战斗节点 `dual_track`/`closed_loop`。KC-006 主线题由审核 ID 84 自动批准，第二名学生从共享库取题并正式判题通过；KC-011 也已有普通主线自动批准题，但尚无正式学生判题或掌握度证据。源码链路已确认节点 `challengeKind` 会传给后端，后端按 KC/题型查审核库，未命中再把题型交给 Graph；尚无一次合格活动 run 的端到端题型生成与刷新恢复证据。地图不预检题库覆盖，生成失败时仍依赖重试或结束本局。10/15 本地只读聚合看到 21 条 `status=ACTIVE` run（16 challenge、3 map、2 over）；满足第 2 章以上前沿先修门槛的学生仅 1 个，且没有活动 run。测试/历史账号构成未知，不能据此推断真实用户完成率；仍需通过正常学习证据建立可恢复 run，不绕过先修或伪造掌握证据。
- [ ] Web 验收：主要路由/动作矩阵仍需完成。11 条主页面路由已有真实浏览器路由壳检查（0 页面错误、0 个 5xx）；当时为避免写入拦截了 `/api/game/bootstrap` 和认证刷新，因此不计为页面初始化或完整动作通过。刷新请求由每次整页加载时的 `hydrateFromStorage()` 正常触发，逐 URL 重载解释了多次请求；认证/角色守卫、管理员用户停用/恢复、World HUD 未评估 Twin → 主线地图另有真实验证。主线章节抽屉开合、进入学习页再返回通过 1/1；小镇 HUD/暂停菜单反复开关并进入工坊通过 1/1；主线章节键盘聚焦及 Enter 打开章节通过 1/1；指南页目录锚点更新 URL 并滚动到目标通过 1/1；本轮 `/library` 的「for循环」检索显示带页码证据，1/1；`/board?tab=classroom` 深链及班级→历史→今日页签切换通过 1/1；`/profile` 学习目标自评已更新，PUT 仅含 `scoreDelta`，`evidenceCount`/`confidence` 不变。三条新动作均使用临时学生并已清理，没有手动触发模型或判题。共享表格样式对齐已完成。管理台审核检索/驳回已有本地浏览器记录；当前无待审核项，不重复改变共享题库。剩余 Web 页面动作仍待验收。
- [x] Web 页面动作续验：题库按 KC 筛选、题号搜索、清空恢复和“去做”进入训练题均由真实浏览器验证；复盘大厅未评估 Twin 推荐已修复并复验进入主线地图。`/examples` 已退役，路由静态重定向到 `/problems` 并保留 `kcCode` 筛选；其题库筛选动作已有浏览器证据。相关临时账号已清理，已验证动作页面错误与模型请求均为 0。
- [x] 题库深链边界：有效 KC 暂无编程题时保留 `kcCode` 并显示“0 题”；未知 KC 仍回退到全部。回归先 RED 后 GREEN，`ProblemListPage` 10/10、typecheck、定向 ESLint 通过。修复后真实 Chromium 验证 `/examples?kcCode=PY43-KC-001` 跳转并保留所选 KC；320px 下无横向溢出，页面错误与模型请求均为 0。
- [x] 窄屏抽样：320px Chromium 检查 `/mainline/stage/2`、`/problems`、`/profile-room`、`/library`，页面宽度均未溢出；学习分身“近期变化”折叠区在窄屏可展开。页面错误与 5xx 为 0，没有业务写入或模型请求，临时账号已清理。此为抽样，不代表完整路由矩阵。
- [ ] 工程收尾：按 Web 审查清单继续修复可复现的命名、交互、错误态或可访问性缺陷；strict TypeScript 源码扫描未发现 `any` / `as any` 或空命名 `void` 函数；工坊 `quiz_finished` 的过时 TODO 已改为说明客户端事件不作可信证据，未改运行逻辑。
- [x] Maven 全量失败已做报告归因：记录的 7 个错误里，6 个是 Testcontainers Docker 动态宿主端口绑定失败，1 个是长期仿真的 PostgreSQL 连接重置；0 个断言失败。尚未修复 Docker/连接环境，也未重跑全量套件。
- [x] Tutor SSE 长等待的本地运行态根因已定位并修复：Graph 源文件比正在运行的容器新，Uvicorn 命令没有自动重载，旧进程未加载心跳改动。开发 Compose 限定监视 Graph 源码目录并启用 reload；Compose 契约先因缺少 reload 断言失败，修复后通过；重建容器后 `/healthz` 200、容器 healthy；临时 `.py` 文件触发 WatchFiles 重载后删除，服务仍健康。
- [ ] 生产环境核对：公网边缘限流、Piston 网络 ACL 与 ECS 运行态待确认。Piston Compose/preflight 静态契约已通过，但 Piston API 本身不验证 Bearer token，必须由私网 ACL 或代理实施访问控制。当前 SSH 仍走 TUN 路由，需要 sudo 才能改本机直连路由；不尝试远端写入或部署。
- [ ] 学习效果：Twin A/B 和 7 日延迟复测需有合适真实样本，现阶段不可宣称有效提升。
- [ ] 后续增强（已按精简计划暂缓）：未来分岔的真实学习结果评估与 BKT 概率模型不属于当前闭环验收门槛。
- [x] DeepSeek Web Tutor 全链路文本验收：运行态先出现 Graph 意图路由 401，核实为 Java 缺内部令牌、Graph 使用 `.env` 占位令牌；按 `./dev.sh` 重启后两端共享临时强令牌。单次 Playwright 请求命中 `/agents/intent/route` 与 `/agents/tutor/ask/stream`，均 HTTP 200，非空文本断言通过（1/1，7.2 秒）；临时账号已清理。不要重复 provider 探针或图片调用。
- [ ] 性能：最后执行，功能和工程验证收敛后再测。

### 2026-10-15 当前续接快照

- 开始时 HEAD：`d5e4823b`，工作树干净；只读 Tutor/Alethicode、远征与安全审查未重跑模型或云端操作。随后只做了 RAG/公告板/画像的 Web 功能核验，跟踪文档有未提交更新。
- AutoGen Tutor 已按固定阶段运行并有 run-scoped checkpoint；与 Alethicode 的事件/API 生命周期有差异，但直接迁移其 V124/线程调度会重复持久化并破坏当前合同，无需再做一次“替换”。
- 本轮已有的历史 DeepSeek 文本/PNG 成功不能证明服务持续稳定；随后出现过 relay 超时、空 JSON 与 503。只在具体未完成产品路径变化后再做一次有针对性的调用，不重复通用探针。
- 本轮审查未发现确认的高危代码缺陷。Piston 生产访问控制、符合先修条件的活动远征 run 恢复、KC 题型覆盖、Web 动作矩阵与 Twin 学习成效仍开放；性能继续最后。
- Piston 本地部署复核确认开发 Compose 只绑定 `127.0.0.1:2000`，现有安全契约也断言回环绑定；生产 Compose 不托管 Piston，依赖外部 URL 且只接受 HTTPS 或私网 HTTP。外部 Piston 是否真正有鉴权代理/网络 ACL 无法由源码确认，本轮未访问 ECS。

### 2026-10-15 本轮页面动作

- 本地 Vite/Spring/Graph/RAG 服务可用；学生账号访问 `/library` 并查询「for循环」，浏览器验收 1/1，得到带页码证据。学生角色的服务端直接 RAG 端点受限，页面走本地检索回退；该动作未调用 DeepSeek。
- `/board?tab=classroom` 深链选中班级任务页签，再切换历史路径和今日任务；目标内容和 `aria-selected` 状态正确，页面错误 0、模型请求 0，临时账号已清理。
- `/profile` 学习目标滑块提交一条自评；浏览器请求仅有 `scoreDelta`，后端重新读取后 `evidenceCount`/`confidence` 与提交前一致，页面错误 0，临时账号已清理。
- 临时 QA 学生已按精确用户名删除并确认计数为 0。没有重复跑 Tutor/DeepSeek、远征或性能测试。

### 2026-10-15 题库深链筛选修复

- 浏览器发现 `/examples?kcCode=PY43-KC-001` 正确重定向到 `/problems`，但该有效 KC 当前没有编程题，页面误将它当作未知 KC 并清空筛选。
- 保留题库 KC 元数据中的有效筛选，在下拉框显示该 KC 的“0 题”选项；不存在的 KC 仍清除。新增回归先 RED（筛选值为空）后 GREEN。
- `npx vitest run tests/pages/ProblemListPage.spec.ts`：10/10；`npm run typecheck`、定向 ESLint、`git diff --check` 通过。用户 678 清理核对为 0。
- 这条零题目边界只跑组件回归，没有重复浏览器路线或任何 DeepSeek 请求。接续工作转向清单中的 KC-006 远征题覆盖。

### 接续顺序

1. 远征活动 run 恢复仍缺四个根 KC 的战斗题型覆盖及合格学习账号的活动 run；KC-006 的普通主线题现已批准入库（kind 为空，不能用于常规远征节点），下一步需按远征节点类型覆盖并通过正常学习路径建立所需先修证据，不绕过阶段门槛或伪造掌握度。KC-002 的主线生成、入库及正式判题已验证。
2. 完成剩余 Web 路由/动作矩阵；管理员用户停用/恢复和审核检索/驳回已有证据，不重复测试。审核写入只在可安全清理的本地审核记录上验证；当前队列没有待审核记录。
3. 工程收尾只修复可复现问题；已验证的 DeepSeek provider 文字/图片能力和 Tutor 文本链路不重复探针，除非相关代码、配置或传输再次改变。
4. Maven/Testcontainers 环境错误已通过 Surefire 报告归因；Docker/PG 环境稳定后再决定是否重跑。生产边缘、Piston ACL 与 ECS 运行态核对仍待直连条件，不改远端状态。
5. 在功能与工程项目收敛后才做性能采样。

---

## Task 1: Reject credentialed wildcard CORS origins

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/auth/security/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/pythontopia/auth/security/SecurityConfigCorsTest.java`

**Steps:** Add a failing assertion that a subdomain wildcard is rejected; run `mvn -q -Dtest=SecurityConfigCorsTest test`; change allowed origin patterns to exact origins and reject any `*`; verify configured exact origins still match; rerun the focused test and Spotless for touched Java.

**Status:** Code and CORS regression tests complete in `f2176675`. Focused Maven tests pass; focused Spotless still reports existing formatting drift in the touched Java files, so whole-file formatting churn was not kept.

## Task 2: Stop returning raw media composition exceptions

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/media/StoryboardCompositionService.java`
- Test: `backend/src/test/java/com/pythontopia/media/StoryboardCompositionServiceTest.java`

**Steps:** Add a test using a temporary failing ffmpeg stub that emits a sentinel path; verify RED exposes the sentinel; return a stable generic `VIDEO_COMPOSE_FAILED` message while retaining the server log; verify GREEN, TTS stays disabled, and run the focused test.

**Status:** Complete in `f2176675`; the sentinel test and media service tests pass. The focused Spotless limitation above also applies.

## Task 3: Lock existing public-edge request limits with a regression check

**Files:**
- Modify: `deploy/scripts/test_https_forwarded_proto_contract.sh` or create a focused shell contract test under `deploy/scripts/`
- Check: `deploy/nginx/pythontopia.conf`

**Steps:** Add assertions for registration/login and classroom-join `limit_req_zone`/locations; run the script before and after the minimum test-only change; document that enforcement is at the public Nginx boundary and production edge behavior still needs deployment verification. Do not add a DB rate-limit table.

**Status:** Complete in `f2176675`; all nine HTTPS/Nginx contract assertions pass. Public-edge runtime behavior remains unverified.

## Task 4: Revalidate DeepSeek-only Tutor and fixed AutoGen lifecycle

**Files:**
- Check: `services/pythontopia-graph/pythontopia_graph/llm/provider.py`
- Check: `services/pythontopia-graph/pythontopia_graph/graph/autogen_tutor_runtime.py`
- Check: `services/pythontopia-graph/pythontopia_graph/routes/tutor.py`
- Check: `services/pythontopia-graph/pythontopia_graph/routes/tutor_workflow.py`
- Test: existing Tutor AutoGen, checkpoint, attachment, and provider tests

**Steps:** Verify supported model host/name allowlists, that image input reaches DeepSeek but response schema remains text-only, that voice is disabled, and that cancellation/checkpoint restore are scoped. Run focused pytest and Java Tutor authorization/checkpoint tests. Do not migrate Alethicode's uncommitted controller, WebSocket bus, or V124 table.

**Status:** Static model/host allowlists, image-input/text-only response filtering, disabled TTS, and fixed-stage AutoGen behavior are confirmed; mocked provider/image/SSE tests pass. On 2026-10-15 the latest Tutor SSE text+image requests timed out at the 120 s Tutor→Graph relay. In a later controlled provider-level check this turn, one direct text completion returned `PYT-TEXT-641`, and one synthetic PNG request returned the correct description “蓝色圆形 / 黄色背景”; both responses were text with no tool calls. Historical full Vite→Spring→Graph text+image verification also succeeded. The latest full Tutor Web relay remains unverified; do not repeat provider probes while progressing other tasks. The RED/GREEN first-token heartbeat change still needs a runtime-container reload and its separate slow-first-token verification.

## Task 5: Verify the existing expedition and twin loop in the browser

**Files:**
- Check: `frontend/src/pages/ExpeditionPage.vue`, Phaser Boss scene, `frontend/src/pages/ProfileRoomPage.vue`
- Check: `backend/src/main/java/com/pythontopia/world/boss/ExpeditionRunService.java`, `BossService.java`, `LearnerTwinService.java`, `AdaptivePracticePolicy.java`
- Test: existing boss scene, expedition run, Twin page/service tests

**Steps:** Exercise map entry, challenge answer, server settlement, pass/fail visual result, reload/resume, and one Twin recommendation in local Web stack; inspect API/browser state and cleanup temporary test users/runs. Keep model generation off by selecting existing reviewed/golden tasks. Record the final Boss journey as pending if no valid accessible multi-phase task exists; do not bypass prerequisites or invent mastery.

**Status:** Prior Web evidence records a real three-phase Boss victory, server settlement, and Phaser hit sequence. Twin home/profile routes now share one tested destination rule. An undecidable/stale expedition problem now has a tested replacement-question path that preserves health and settlement state. Current focused rerun passed 76 tests across Twin navigation, HUD drag, and expedition page/store, plus typecheck, touched-file ESLint, and `git diff --check`. A browser refresh of an active server run remains open: the current local course has no approved/playable code question for its four root KCs, and no valid eligible learner session is configured. Do not create synthetic mastery to bypass this.

## Task 6: Audit Web interaction and admin table behavior

**Files:**
- Check: `frontend/src/pages/admin/`, `frontend/src/components/admin/`, major student/teacher Web routes, shared loading/error/empty-state components
- Test: existing Vitest and Playwright suites

**Steps:** Build a route/action checklist from current routes; use Playwright to click visible actions and verify navigation, success/error/loading state, focus and narrow-screen overflow. Fix only confirmed dead actions/layout defects using existing components. Do not add pages without a real backend contract.

**Status:** Partial: fresh Twin → mainline navigation and 320 px overflow checks passed. This continuation also followed the visible probe-dismiss action, clicked the World HUD Twin recommendation, and observed `/mainline-map` with 0/65 assessed KCs, no mastery change, and no browser page errors; the temporary student was deleted. The shared draggable hook now lets ARIA button clicks pass without starting a drag. Admin component/page suites passed (8 files / 36 tests), admin user API and router suites passed (2 files / 22 tests), and current full frontend typecheck, lint, and production build passed. In this continuation, nine protected routes redirected an anonymous browser to `/login`; a student was redirected away from four admin/teacher routes. A temporary local admin then searched a temporary user, disabled it (login returned 401), re-enabled it (login returned 200), and both accounts were deleted. At 320 px the admin page document stayed 320 px wide with no page errors. W3C table and pagination semantics are present. Shared table headers are now centered, the radius uses the existing 16px panel token, and pagination is centered while retaining manual page jump; the five affected table/user/review specs pass (26 tests). Five known operations keys now render as readable Chinese recommendations, with raw values retained in tooltips; `AdminOperationsPage` passes 5 tests. Fresh `npm run typecheck` and file-scoped ESLint pass. Fresh real-browser action checks passed HUD/pause-menu → workshop (1/1), keyboard focus/Enter → mainline chapter (1/1), and guide TOC anchor navigation (1/1); each used one disposable student, deleted after use, with no AI/model request or judging. Remaining work: major route/action matrix and narrow-screen checks outside previously tested pages. Admin review search/rejection has earlier real-browser evidence; the current queue has no pending item, so a new decision-write check is deferred until there is a safe disposable record.

**Engineering scan:** The current Web `src/` tree has no explicit `any`, `as any`, or empty named `void` functions in the scanned TypeScript/Vue source. Empty arrow callbacks found were two fullscreen error catches and intentionally pending test promises. No broad rename or formatting pass was justified by the findings.

## Task 7: Run final affected-surface verification and report measured limits

**Commands:**
- `cd backend && mvn -q -Dtest=SecurityConfigCorsTest,StoryboardCompositionServiceTest test`
- `cd services/pythontopia-graph && .venv/bin/python -m pytest -q <focused tutor/boss tests>`
- `cd frontend && npm run test -- <affected specs> --coverage.enabled=false && npm run typecheck && npm run lint && npm run build`
- `git diff --check` and `deploy/scripts/test_https_forwarded_proto_contract.sh`

Run performance sampling last. Separate deterministic API timing from Graph/DeepSeek and formal judging; report request counts and clean up disposable rows. Never describe prior or synthetic data as current performance evidence.

**Status:** Affected-surface checks pass: frontend Twin/Boss recovery, admin components/pages/API/router, typecheck, lint, and production build; backend CORS/media focused tests (8), Graph Boss suite (257), Graph full suite (1871 passed, 1 skipped), RAG full suite (158 passed, 2 skipped), Nginx contract checks (9), and `TutorDailyRequestLimitServiceTest` pass. The current continuation added a combined five-file frontend regression run (76 tests). Surefire XML confirms the recorded serial Maven run had 0 assertion failures and 7 environment errors: six Testcontainers containers could not bind random Docker host ports (`address already in use`) and the long-term simulation lost its PostgreSQL connection. No full Maven rerun was made during this continuation. A fresh single Web text flow now passed after aligning local Graph/Java internal-token runtime configuration; it reached both intent routing and Tutor SSE with HTTP 200 and returned non-empty text. A separate KC-004 Web question request passed after sandbox alignment; this used one UI request and six DeepSeek completions because the first five candidates failed output self-tests. Performance remains intentionally unmeasured until the remaining functional/runtime checks close.

## Task 8: Protect classroom join codes per authenticated account

**Status:** Complete in `36bb7abe`. Existing Nginx source-IP rate limiting remains; backend additionally limits each signed-in account to 30 code attempts per 10 minutes before database lookup. RED/GREEN service tests, HTTPS proxy contract, and Nginx dual-protocol checks passed. `JoinCodeAttemptThrottleTest` and `ClassroomServiceTest` passed. Process-local state assumes the documented single-worker deployment; ECS runtime was not inspected.

## Task 9: Redact unexpected Tutor workflow errors

**Status:** Complete in `36bb7abe`. Unexpected exceptions are logged server-side with stack traces and returned to the browser as a fixed `TUTOR_WORKFLOW_FAILED` envelope. The regression test reproduced credential-like text in the old SSE response before the fix. Tutor workflow, AutoGen, attachment, coach graph, task events, ask stream, and image-input suites passed (93); Ruff passed.

## Current environment note

The configured SSH destination resolves through the TUN default route. The repository's WSL instructions require adding a direct host route, but this session cannot obtain the local sudo password, so no ECS SSH connection was attempted. Production Piston and Nginx runtime state remains unverified. Static production configuration requires the Piston URL/version/token and restricts the URL to HTTPS or a private address; the Piston API does not validate its Bearer token, so the real network ACL or an auth-checking proxy must be confirmed before production sign-off. Repository configuration was not changed.
