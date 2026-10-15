# Learner Twin First Action — 2026-10-15

## Finding

An unassessed KC's server recommendation says to establish evidence with a basic exercise, but `ProfileRoomPage` sent every non-review recommendation to `/expedition`. New learners can have no eligible coding question there, so the primary Twin action led to a known `EXPEDITION_NO_PRACTICE_PROBLEMS` response.

## Change

- Route unassessed or missing KC state to the existing `/mainline-map` and label it “前往主线学习”.
- Keep due review routing and assessed recommendation routing unchanged.
- Add a page regression covering the unassessed label and navigation; update the assessed alternative fixture so it still represents a verified KC.

## Verification

- RED: `npm run test -- tests/pages/ProfileRoomPage.spec.ts` failed because the button displayed “前往无尽远征”.
- GREEN: the same focused suite passed, 4/4.
- Full frontend `npm run test`: 275 files, 2596 tests passed.
- `npm run typecheck`, ESLint for the changed page/spec, and `git diff --check` passed.
- Real browser using a disposable student: `GET /api/learner/twin/state` returned HTTP 200 with 0/65 assessed KCs; the action displayed “前往主线学习” and navigated to `/mainline-map`; browser errors 0. The account was logged out and deleted.

## Still open

This verifies the first action only. Completing the mainline lesson and assessment through the first eligible programming KC remains pending; no mastery was fabricated.

## 2026-10-15 — Shared recommendation routing

- Follow-up review found the home panel still sent an unassessed server recommendation to `/expedition`, while the Twin page correctly used `/mainline-map`. The shared `routeForTwinRecommendation` helper now applies the same rule in both callers: due review → `/bug-mine`, unassessed or missing KC evidence → `/mainline-map`, assessed KC → its focused expedition.
- RED/GREEN: the home recommendation regression first failed on the old expedition destination; a page-level Twin regression covers the same unassessed entry. The combined current rerun passed Twin and expedition specs together.
- Verification: 5 files / 76 Vitest tests, including the shared drag-hook regression; `npm run typecheck`; ESLint for changed source/spec files; `git diff --check`.
- No model or Graph call was made. The first eligible real programming KC and active server-run browser recovery remain blocked by lack of an approved matching question and eligible test account.

## 2026-10-15 — World HUD pointer acceptance

- The first browser attempt exposed a real modal overlay: the optional `cog_intro` probe captures pointer input until dismissed. Following the visible “跳过” action exposed a second issue in the test selector only: expansion changes the header's accessible name from “展开” to “折叠”.
- After using the actual probe dismiss button and querying the stable header element, a fresh learner's mouse click expanded the panel and the weak-KC recommendation navigated to `/mainline-map`. Twin state stayed 0/65 assessed, no browser page errors occurred, and the temporary account was deleted.
- The drag hook regression covers the underlying pointer contract: ARIA button pointerdown must not call `preventDefault`. This test failed before the selector change and passed after it.

## 2026-10-15 — AI provider egress static audit

- No DeepSeek or other model request was sent during this audit.
- Graph provider validates HTTPS `api.deepseek.com` and an explicit DeepSeek model allowlist; Compose maps its compatibility aliases to the same DeepSeek configuration. The RAG path uses local hash/BM25 retrieval.
- Legacy Xfyun TTS, ASR, image generation, and image-understanding services all hard-return disabled. Their compatibility endpoints fail closed or return unavailable before any upstream call; the old image-understanding adapter has no production caller. Tutor image input stays on Graph's DeepSeek vision path and responses remain text-only.
- No implementation change was needed. Provider-level real text/image coverage is already recorded above; the Tutor Web SSE relay's latest timeout remains a separate, open transport issue and will not be retried without a relevant code/configuration change.

## 2026-10-15 — KC-011 AI 编程题闭环

- 只读检查确认 `PY43-KC-011` 原本没有课程编程题、已批准题或待审题；所属主线为第 2 章「变量、类型与运算」。
- 使用现有学生题目请求端点 `POST /api/world/boss/2/problem` 一次，锚定 KC-011。响应 HTTP 200、`aiGenerated=true`、`reviewed=true`；题目读入整数与浮点数并输出类型相关结果。数据库记录为 `APPROVED/AUTOMATED`，来自同 KC 共享题库路径。
- 自动审查要求完整参考解逐条通过隔离 Piston 测试；不合格或沙箱不可用时 fail closed。只检查了公开题面与样例，没有读取或披露隐藏用例、参考答案。
- 未提交学生作答；KC-011 的掌握度事件数为 0。保留一个仅用于题库审核外键的随机匿名请求账号。
- 这是面向缺题 KC 的真实产品出题流程，不是 provider 探针；不重复请求。远征根 KC-001/002/004/006 缺题和活动 run 恢复仍未解决。

## 2026-10-15 — 主线学习路径浏览器验收

- 运行 `deliveryClickRobustness.spec.ts` 中主线章节抽屉场景：打开/关闭章节抽屉、进入学习页、切换目录后返回地图；Chromium 1/1 通过，5.1 秒。
- 该流程只读取课程/主线数据，没有点击 AI 编程练习或提交判题；临时学生账号删除 1 条。没有发起 DeepSeek 请求。
- 这只覆盖一条路径；其它未验证页面和动作仍保留在总清单中。

## 2026-10-15 P0 follow-up

- Confirmed `SelfDimensionRequest` accepts only `scoreDelta` and `tagsAdd`; its conversion writes no evidence count or metadata. `LearnerTwinService` builds state from batched authoritative mastery, recent practice, last-practiced, prerequisite, and review-state reads. `WeeklyReportService` uses one Asia/Shanghai seven-day window, separates total authoritative events from its 30-memory display cap, and reads actual weekly mastery-event deltas in one grouped query.
- Found one cross-module duplicate: a successful Bug Mine submission publishes `PracticeSucceeded`, then session resolution published `DebugResolved`, applying two mastery deltas and two review outcomes for the same verified submission. `DebugResolved` now carries a server-created idempotency key. Bug Mine reuses `submission:<id>` so mastery deduplicates the second event; profile projection uses `debug_resolved:<user>:<submission-key>` so distinct attempts on the same problem remain distinct error-pattern evidence. Manually completed fix-bug quests use a quest-specific key.
- RED/GREEN: `LearningEventListenerTest` failed before the event carried an attempt key and passed after the shared key was wired. Focused Twin/profile/mastery/quest/Bug Mine tests: 110 passed; isolated `Stage10BugMineAcceptanceIntegrationTest#retrySubmitResolvedSessionAndIncrementsMastery` passed with PostgreSQL and verified one event per successful retry; `git diff --check` passed.
- The real learner completed all three first-chapter stages and submitted a first final exam: test 14 failed at 55 while the local Piston URL was absent, then test 15 passed at 98/100 with 2 stars after Piston was configured. All nine coding items passed server Piston; two objective items had malformed/wrong generated content and are excluded from mastery evidence. The map unlocked chapter 2. This result predates the change below and was intentionally not backfilled.

## 2026-10-15 — Chapter coding assessment evidence

- `ChapterTestService` publishes one internal learning event per KC after the submitted status is persisted. It aggregates only `CODING` items and server `QuestionJudgement`s; a KC passes only when all coding items pass. Choice, judge, and fill answer keys are excluded until generated objective content has reliable review.
- `LearningEventListener` applies the existing `MICRO_CHECK_PASS/FAIL` source with an idempotency key based on assessment kind, chapter, assessment ID, and KC. Existing mastery evaluator updates both `kc_mastery_event` and FSRS-lite; no new table, API, service, or model was added.
- `MemoryStreamMirror` records the chapter/KC and verified coding counts in the existing event log.
- First PostgreSQL integration run caught the configured `pythontopia.mastery.deltas` map missing `MICRO_CHECK_PASS` and `MICRO_CHECK_FAIL`; because the map was non-empty, the property defaults were not used and both deltas were zero. Added `+0.08/-0.02` to `application.yml`.
- Verification: targeted Maven unit tests passed for `ChapterTestServiceTest` (20), `ChapterTestJudgeTest` (10), `LearningEventListenerTest` (2), and `MemoryStreamMirrorTest` (6). `ChapterTestSubmitConcurrencyIntegrationTest` passed 2/2 with PostgreSQL; publishing the same event twice persisted one idempotent mastery row and a positive mastery score. `git diff --check` passed.
- Live Web verification: after restarting `./dev.sh --sandbox`, the same learner took quiz `test_id=16` (attempt 1). The three visible coding tasks were typed through the page and passed Piston 8/8 cases; objective questions were left blank, so the quiz score was 33/100. Integrity was paste=0, blur=0, overtime=false. The Twin API then returned 3/65 assessed KCs: `PY43-KC-003`, `004`, and `006`, each at mastery 0.08 / confidence 0.05. PostgreSQL confirmed exactly one `kc_mastery_event` per KC, each `applied_delta=0.080`, plus three `chapter_assessment_passed` memory rows; browser errors 0.
- Historical scores are not backfilled. KC-010 natural unlock, Tutor/Alethicode live parity, complete Maven stability, real learning-effect comparison, provider billing review, production Piston/ECS, final Boss journey, and performance remain pending.

## 2026-10-15 — Chapter objective item guard

- Hardened the chapter-paper prompt to request exactly one correct choice, clearly incorrect distractors, and fill answers that fit the question and course evidence. The existing retry path now rejects exact duplicate choice text after whitespace/case normalization.
- RED/GREEN: the duplicate-choice regression first failed because the response was accepted, then passed after the structural guard. The chapter-paper Graph suite passed 20/20; Ruff passed for the touched Python files.
- This does not establish semantic correctness for generated answer keys. Objective items remain excluded from Learner Twin mastery updates until their correctness can be verified independently.
- Full backend Maven: `mvn -q test` ran 1,428 tests with 0 assertion failures and 7 environment errors (6 Docker dynamic host-port conflicts, 1 long-term simulation JDBC connection refusal). Re-running the 7 affected classes in separate JVMs passed 5 classes, including the simulation; `LearningEvidenceGraphServiceTest` and `RecommendationServiceTest` still hit `address already in use`. During a failure, the selected host port appeared in Docker bridge `TIME-WAIT`; the local ephemeral port range is 44620–48715. No system network setting was changed, and the full Maven suite remains unverified as green.

## 2026-10-15 — Twin and mainline fresh Web smoke

- Disposable student state returned 0/65 assessed KCs; the Twin displayed “尚未评估” and recommended the first mainline KC without inventing mastery. Clicking the primary recommendation navigated to `/mainline-map`, which showed chapter 1 available and later chapters locked.
- `/profile-room`, `/mainline-map`, and `/expedition` each rendered at a 320 px viewport with `documentElement.scrollWidth=320`. No browser page errors or failed HTTP requests occurred.
- Student fixture and account were deleted after the smoke. No practice submission or mastery change was created; actual lesson completion and assessment remain pending.
