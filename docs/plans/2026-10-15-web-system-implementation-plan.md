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
- [x] DeepSeek provider 真实文本和合成图片输入均成功返回文本；项目不请求图片生成，TTS 关闭。
- [x] 无尽远征采用 Phaser Boss 场景，包含判题受击/失败体力反馈及登记过的 CC0 音效；真实三阶段通关已有浏览器记录。
- [x] AI 编程题复用现有审核与共享题库；KC-010 已真实审核并由 Piston 判对，KC-011 仍待补题闭环。
- [x] Twin P0 状态聚合、证据可信度、周报真实增量、共享 AdaptivePracticePolicy 和带证据/下一步的学习分身页面已有代码与定向验证。
- [ ] 远征：用有效先修学习账号从服务端活动 run 刷新并恢复；本地根 KC-001/002/004/006 当前均无课程编程题或已批准 AI 编程题，且没有可用的合格账号会话；不要伪造掌握证据。
- [ ] Web 验收：管理台真实管理员写操作和主要 Web 路由/动作矩阵仍需完成。匿名保护路由和学生角色拒绝管理/教师路由已验证。
- [ ] 工程收尾：按 Web 审查清单继续修复可复现的命名、交互、错误态或可访问性缺陷；strict TypeScript 源码扫描未发现 `any` / `as any` 或空命名 `void` 函数；工坊 `quiz_finished` 的过时 TODO 已改为说明客户端事件不作可信证据，未改运行逻辑。
- [ ] 环境/线上核对：全量 Maven 的 7 个环境错误、生产边缘限流与 Piston 网络 ACL 运行态、ECS 状态待确认；Piston Compose/preflight 静态契约已通过，但 Piston API 本身不验证 Bearer token，必须由私网 ACL 或代理实施访问控制。当前 SSH 需要 sudo 修改本机直连路由，本轮未尝试远端连接；不得推送或部署。
- [ ] 学习效果：Twin A/B 和 7 日延迟复测需有合适真实样本，现阶段不可宣称有效提升。
- [ ] 后续增强（已按精简计划暂缓）：未来分岔的真实学习结果评估与 BKT 概率模型不属于当前闭环验收门槛。
- [ ] DeepSeek Web Tutor：历史 Vite→Spring→Graph 文图闭环成功过；2026-10-15 最新 SSE 联测超时。provider 层本轮文字和图片输入复验成功；先完成其他功能，不重复发模型探针。
- [ ] 性能：最后执行，功能和工程验证收敛后再测。

### 接续顺序

1. 先处理无需新增模型调用的远征服务端 run 恢复验证及 Web 交互检查。
2. 完成可获得合法管理员会话时的管理台浏览器操作验收，并清理临时账号。
3. 按复现结果做最小 Web 工程修复；每项只跑受影响验证。
4. 单独归因 Maven/Testcontainers 环境错误并核对生产配置，不改远端状态。
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

**Status:** Prior Web evidence records a real three-phase Boss victory, server settlement, and Phaser hit sequence; recent Web evidence also verified the zero-evidence Twin recommendation and mainline navigation. Current 76-test run-store/page/Boss/scene suites cover mocked reload settlement, resume, failure feedback, and retries. A browser refresh of an active server run remains open: the current local course has no approved/playable code question for its four root KCs, and no valid eligible learner session is configured. Do not create synthetic mastery to bypass this.

## Task 6: Audit Web interaction and admin table behavior

**Files:**
- Check: `frontend/src/pages/admin/`, `frontend/src/components/admin/`, major student/teacher Web routes, shared loading/error/empty-state components
- Test: existing Vitest and Playwright suites

**Steps:** Build a route/action checklist from current routes; use Playwright to click visible actions and verify navigation, success/error/loading state, focus and narrow-screen overflow. Fix only confirmed dead actions/layout defects using existing components. Do not add pages without a real backend contract.

**Status:** Partial: fresh Twin → mainline navigation and 320 px overflow checks passed. Admin component/page suites passed (8 files / 36 tests), admin user API and router suites passed (2 files / 22 tests), and current full frontend typecheck, lint, and production build passed. In this continuation, nine protected routes redirected an anonymous browser to `/login`; a newly registered student was redirected away from four admin/teacher routes to `/world`, then the account was deleted. W3C table and pagination semantics are already present. Admin authenticated write actions, remaining major route actions, and narrow-screen checks outside the previously tested pages remain open because no disposable ADMIN session is configured.

**Engineering scan:** The current Web `src/` tree has no explicit `any`, `as any`, or empty named `void` functions in the scanned TypeScript/Vue source. Empty arrow callbacks found were two fullscreen error catches and intentionally pending test promises. No broad rename or formatting pass was justified by the findings.

## Task 7: Run final affected-surface verification and report measured limits

**Commands:**
- `cd backend && mvn -q -Dtest=SecurityConfigCorsTest,StoryboardCompositionServiceTest test`
- `cd services/pythontopia-graph && .venv/bin/python -m pytest -q <focused tutor/boss tests>`
- `cd frontend && npm run test -- <affected specs> --coverage.enabled=false && npm run typecheck && npm run lint && npm run build`
- `git diff --check` and `deploy/scripts/test_https_forwarded_proto_contract.sh`

Run performance sampling last. Separate deterministic API timing from Graph/DeepSeek and formal judging; report request counts and clean up disposable rows. Never describe prior or synthetic data as current performance evidence.

**Status:** Affected-surface checks pass: frontend Twin/Boss recovery, admin components/pages/API/router, typecheck, lint, and production build; backend CORS/media focused tests (8), Graph full suite (1871 passed, 1 skipped), RAG full suite (158 passed, 2 skipped), Nginx contract checks (9), and `TutorDailyRequestLimitServiceTest` pass. A serial Maven rerun reached 1430 tests with 0 assertion failures but 7 environment errors: six Testcontainers containers could not bind random Docker host ports (`address already in use`) and the long-term simulation lost its PostgreSQL connection. Historical and current provider-level DeepSeek text/image calls succeeded, while the latest full Tutor Web relay timed out; no further model probe is planned during the remaining functional work. Performance remains intentionally unmeasured until the remaining functional/runtime checks close.

## Task 8: Protect classroom join codes per authenticated account

**Status:** Complete in `36bb7abe`. Existing Nginx source-IP rate limiting remains; backend additionally limits each signed-in account to 30 code attempts per 10 minutes before database lookup. RED/GREEN service tests, HTTPS proxy contract, and Nginx dual-protocol checks passed. `JoinCodeAttemptThrottleTest` and `ClassroomServiceTest` passed. Process-local state assumes the documented single-worker deployment; ECS runtime was not inspected.

## Task 9: Redact unexpected Tutor workflow errors

**Status:** Complete in `36bb7abe`. Unexpected exceptions are logged server-side with stack traces and returned to the browser as a fixed `TUTOR_WORKFLOW_FAILED` envelope. The regression test reproduced credential-like text in the old SSE response before the fix. Tutor workflow, AutoGen, attachment, coach graph, task events, ask stream, and image-input suites passed (93); Ruff passed.

## Current environment note

The configured SSH destination resolves through the TUN default route. The repository's WSL instructions require adding a direct host route, but this session cannot obtain the local sudo password, so no ECS SSH connection was attempted. Production Piston and Nginx runtime state remains unverified. Static production configuration requires the Piston URL/version/token and restricts the URL to HTTPS or a private address; the Piston API does not validate its Bearer token, so the real network ACL or an auth-checking proxy must be confirmed before production sign-off. Repository configuration was not changed.
