# DeepSeek 导学与可信学习数据实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让 DeepSeek 成为唯一生成式 AI 提供方，确保导师文字/图像对话可用，并封住学生判题、远征奖励和学习画像中的可伪造边界。

**Architecture:** 复用 Pythontopia 当前 Java API、Graph、AutoGen 五阶段 tutor、DeepSeek OpenAI-compatible provider 与 PostgreSQL 业务投影。服务端判题与数据库事件是学习/远征成效的权威来源；前端事件只作为观察信号。

**Tech Stack:** Java 21、Spring Boot、Maven、FastAPI、AutoGen AgentChat、Vue 3/TypeScript、Vitest、PostgreSQL。

---

## Task 1: 审核 DeepSeek 配置与图文聊天链路

**Files:**
- Review: `deploy/.env.example`, `deploy/docker-compose.yml`, `services/pythontopia-graph/pythontopia_graph/llm/provider.py`
- Review/modify if needed: `frontend/src/components/workshop/WorkshopChat.vue`, `frontend/src/api/tutor.ts`, `backend/src/main/java/com/pythontopia/tutor/TutorService.java`, `services/pythontopia-graph/pythontopia_graph/routes/tutor.py`
- Test: `services/pythontopia-graph/tests/test_tutor_image_input.py`, `services/pythontopia-graph/tests/test_openai_compat_provider.py`, `frontend/tests/components/workshop/WorkshopChat.spec.ts`

1. Verify `deploy/.env` key is ignored and never print its value.
2. Assert text requests select text/fast model and image requests select `deepseek-flash`.
3. Assert invalid media bytes, unsupported formats, oversized images, and more than three attachments fail at schema boundary.
4. Verify image data is omitted from chat history/checkpoints and response remains text.
5. Run focused Graph and Workshop component tests; run frontend typecheck.

## Task 2: Authentication and secure judge default

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/auth/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/pythontopia/submission/judge/PistonPythonSandbox.java`, `SubprocessPythonSandbox.java`, `backend/src/main/resources/application.yml`
- Modify: `deploy/docker-compose.yml`, `deploy/docker-compose.production.yml`, `deploy/.env.example`
- Test: `backend/src/test/java/com/pythontopia/auth/security/JwtAuthenticationFilterTest.java`, `backend/src/test/java/com/pythontopia/submission/judge/PistonPythonSandboxTest.java`

1. Keep/add the invalid Bearer token test and verify the filter clears authentication and delegates to Spring Security.
2. Keep/add the unconfigured Piston test; verify readiness is false and execution returns system error without network or subprocess fallback.
3. Remove weak production defaults for JWT signing, teacher invite, and internal RAG credentials; preserve local `deploy/.env` values without showing secrets.
4. Run focused Maven tests and production config contract script.

## Task 3: Prevent cross-user题目缓存与伪 KC

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/world/boss/BossService.java`, `EndlessPregenService.java`
- Review: `backend/src/main/java/com/pythontopia/world/boss/review/`, `backend/src/main/java/com/pythontopia/mainline/ChapterCatalog.java`
- Test: `backend/src/test/java/com/pythontopia/world/boss/BossServiceTest.java`, `EndlessPregenServiceTest.java`

1. Add a failing test where two users use the same incoming `problemId` but receive different hidden tests; ensure evaluation returns only the requesting user's persisted problem.
2. Add a same-user reissued ID test and a global approved-bank issuance test; require unique per-user problem identity.
3. Validate requested KC against pack 43 and chapter mapping before generation or mastery write; reject unknown KC.
4. Derive last practiced KC from actual judge/practice events, not `kc_mastery.last_event_at` after DECAY.
5. Add bounded pre-generation admission and stale `PENDING` recovery with tests for saturation and expiry.
6. Run Boss/pregen focused tests and PostgreSQL migration/integration coverage.

## Task 4: Make expedition scores and class ranking trustworthy

**Files:**
- Review/modify: `backend/src/main/java/com/pythontopia/world/boss/ExpeditionScoreController.java`, `ExpeditionScoreService.java`, `ExpeditionRunService.java`, `backend/src/main/resources/db/migration/`
- Review/modify: `frontend/src/composables/useEndlessRun.ts`, `frontend/src/stores/rogueRun.ts`, corresponding API DTOs
- Test: expedition score/run controller and repository tests; frontend endless-run tests

1. Add a failing test proving repeated or fabricated client score cannot create leaderboard entries.
2. Derive score/reward from a backend-verified run or formally reject client snapshots as score evidence.
3. Scope class leaderboard to one explicit active classroom and make score/depth come from the same run row.
4. Preserve clients by keeping non-authoritative snapshot persistence separate from ranked results.
5. Run focused backend integration tests and frontend API/store tests.

## Task 5: Make Learner Twin evidence time-bounded and source-aware

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/learner/event/LearningEventController.java`, `WeeklyReportService.java`, relevant repositories/DTOs
- Modify if labels require: `frontend/src/components/companion/TwinWeekly.vue`
- Test: `WeeklyReportServiceTest.java`, `WeeklyReportServicePostgresIntegrationTest.java`, event controller tests, Twin component tests

1. Add failing tests proving frontend events cannot create formal practice success/failure evidence or mastery evidence.
2. Apply the same 7-day cutoff to each weekly evidence source; compute mastery delta from window boundaries or label current level as a snapshot.
3. Present evidence coverage/source instead of calling event-count heuristics statistical confidence.
4. Run focused service, PostgreSQL, and component tests.

## Task 6: Keep admin UI API-backed and verify release boundaries

**Files:**
- Review/modify: `frontend/src/pages/admin/`, `frontend/src/app/router.ts`, `frontend/src/App.vue`
- Test: `frontend/tests/pages/admin/`, `frontend/tests/app/router.spec.ts`
- Docs: `docs/project/CHANGELOG.md`, `docs/plans/2026-10-12-*-worklog.md`

1. Preserve the two existing real-data admin features; add scoped visual polish/logout only where API/auth contracts already exist.
2. Reject navigation cards for Alethicode capabilities absent from Pythontopia APIs.
3. Run focused admin tests, frontend typecheck/lint, affected backend and Graph suites.
4. Review `git diff --check`, ensure no key appears in tracked files, update changelog/worklogs with current test output and explicit unverified items.
5. If the full local stack is healthy, run browser/API integration; do not deploy or call production services.

## Execution notes

- Implement in the current authorized workspace because its existing changes are part of this task; do not create a competing checkout or reset/clean the worktree.
- Keep changes surgical and preserve the already-created checkpoint commit `8089dba7`.
- Never stage or commit the entire worktree. Do not claim a live DeepSeek call or production Piston isolation test without current evidence.
