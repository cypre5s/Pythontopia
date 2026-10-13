# Web Security, Expedition, and Learner Twin Implementation Plan

> **For Codex:** Execute this plan in the isolated worktree. Keep DeepSeek calls and performance measurements deferred until the user-requested functional work is complete.

**Goal:** Harden the Web learning path, preserve authoritative expedition scoring, and make the learner twin accurately reflect evidence-backed behavior without claiming calibrated predictions.

**Architecture:** Keep the existing Vue/Spring/FastAPI boundaries and API DTOs. Make expedition policy and score eligibility server-owned, isolate Bilibili proxy configuration from the shared DeepSeek HTTP client, and reuse the existing evidence-backed Tutor, mastery, classroom, and weekly twin services. Audit findings are tracked by severity and evidence; undocumented or unverified areas stay explicitly open.

**Tech Stack:** Vue 3 + TypeScript, Java 21 + Spring Boot + PostgreSQL/Flyway, Python 3.11 + FastAPI/HTTPX/AutoGen, Vitest, JUnit, pytest.

---

## Verified starting point

- Checkpoint: `a8aba47c` (`feat: align AI provider, admin, and expedition safeguards`). The implementation worktree is based on this local commit and is not pushed.
- A separate Android `gradlew.bat` edit exists in the shared worktree. It is unrelated user state and must be preserved.
- No P0 is confirmed by the completed static review. This is not a claim that every application endpoint has had a full adversarial audit.
- Confirmed P1: `video._configure_bilibili_proxy()` writes `PYT_BILI_PROXY` into process-wide `HTTP_PROXY` / `HTTPS_PROXY`; `OpenAICompatibleProvider` constructs HTTPX clients with `trust_env=True`, so the route can change later DeepSeek traffic.
- Confirmed P1: expedition score settlement counts every passed verified question (up to the global 36-row budget); fetching/attempting multiple unique questions against one node can inflate score and depth. Expedition problem issuance also uses request `stage`, `difficulty`, and `preferredKind` without deriving those values from the bound immutable map node.
- Existing positive controls: account role checks and disabled-user token revocation; Tutor image ownership and bounded inline image size; browse URL/DNS/public-address/redirect checks; disabled ASR checked before upload-body reads; Web identity and classroom data are server scoped. Python execution paths are documented as isolated Piston or fail-closed subprocess sandboxes, but deployed Piston isolation still requires environment-level verification.
- Existing functionality: the Tutor route already runs a fixed AutoGen sequence with grounding, hallucination, and safety review. The learner twin already aggregates authoritative learning events, mastery deltas, insights, memories, and resource-run evidence. Admin pages already use real APIs and Pythontopia DTOs; classroom is a teacher area with a different schema from Alethicode.
- Current digital-twin UI calls confidence “证据充分度” and says it is heuristic, not answer probability. Keep this distinction.

## Research and design decisions

- DeepSeek's official vision documentation lists text and image input for `deepseek-flash`; keep image requests on the existing DeepSeek provider and avoid substituting a different model provider: <https://api-docs.deepseek.com/guides/vision/>
- Knowledge tracing literature supports treating learner state as an estimate updated from observations, not as a directly observable fact: Corbett and Anderson, 1995, <https://doi.org/10.1007/BF01099821>.
- Spaced-practice research supports evidence-based review timing, but does not validate this project's current confidence score as a probability: Pavlik and Anderson, 2008, <https://pubmed.ncbi.nlm.nih.gov/18590367/>.
- Digital-twin terminology can overstate personalization or prediction. The Web twin will remain an evidence-linked learner view and planning aid; no uncalibrated forecast or causal claim will be added. See the education-policy discussion at <https://vuir.vu.edu.au/46599/>.
- Expedition difficulty will use immutable map-node difficulty plus the server's existing mastery/profile adjustment. Client momentum, request difficulty, stage, or challenge kind cannot become scoring authority. One node (or one boss phase) may contribute at most one successful score entry; further valid submissions may still produce learning evidence within a bounded attempt policy.

## Task 1: Isolate Bilibili proxy settings

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/routes/video.py`
- Test: `services/pythontopia-graph/tests/test_video_insight_route.py`

1. Add a regression assertion that configuring `PYT_BILI_PROXY` leaves process `HTTP_PROXY` and `HTTPS_PROXY` unchanged.
2. Run the focused pytest and confirm it fails against the current process-environment mutation.
3. Remove the process environment writes. Use the Bilibili library's scoped `request_settings.set_proxy`; when that API is unavailable, do not silently install a process-wide fallback.
4. Run `pytest tests/test_video_insight_route.py -q` and the relevant Graph provider tests.

## Task 2: Make expedition problem parameters server authoritative

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/world/boss/ExpeditionRunService.java`
- Modify: `backend/src/main/java/com/pythontopia/world/boss/BossService.java`
- Tests: `backend/src/test/java/com/pythontopia/world/boss/BossServiceTest.java`
- Tests: `backend/src/test/java/com/pythontopia/world/boss/ExpeditionRunPostgresIntegrationTest.java`
- Frontend contract tests only if DTO behavior changes: `frontend/src/stores/rogueRun.spec.ts` and `frontend/src/composables/useChallengeJudge.spec.ts`

1. Add failing service/integration cases where a valid node request supplies a different stage, difficulty, or challenge kind than the stored node.
2. Resolve a server-owned policy from the immutable node fields and current boss phase. Use node KC, node challenge kind, node difficulty, and a bounded stage derived from that difficulty.
3. For expedition requests only, ignore client difficulty/stage/kind and apply the existing server-side mastery/profile adjustment to the map difficulty. Preserve ordinary Arena/Boss behavior outside expedition.
4. Apply the same policy to on-demand fetch, prefetch, retry, and prefetch-status paths, including reachable-node validation.
5. Run targeted Java unit and PostgreSQL integration tests plus the focused Web store/composable tests. Verify existing request DTOs remain backward compatible.

## Task 3: Bound expedition scoring and retries by node

**Files:**
- Create: `backend/src/main/resources/db/migration/V209__bound_expedition_node_scoring.sql`
- Modify: `backend/src/main/java/com/pythontopia/world/boss/ExpeditionScoreService.java`
- Modify: `backend/src/main/java/com/pythontopia/world/boss/BossService.java`
- Tests: `backend/src/test/java/com/pythontopia/world/boss/ExpeditionScoreServiceTest.java`
- Tests: `backend/src/test/java/com/pythontopia/world/boss/ExpeditionRunPostgresIntegrationTest.java`

1. Add failing cases showing that many unique passing questions for one normal node add at most one score/depth, while separate boss phases can each score once.
2. Add migration metadata, then add nullable node/phase identity and score-eligibility to the verified-attempt ledger. Backfill from `pregenerated_problem` bindings and preserve legacy rows safely; document whether old active runs can settle and the irreversible audit impact.
3. Lock the active run before selecting/updating the score slot. The first successful judgment for a node/phase claims its score slot; failed judgments remain learning evidence and do not claim it. A failure-to-pass transition can claim the slot once. Duplicates remain idempotent.
4. Enforce the existing global run limit and a bounded per-node/per-phase unique-question issuance policy on the server, including prefetch paths, so changing request fields cannot create an unlimited billable retry loop.
5. Settle score/depth only from claimed score slots. Retain verified learning events for later valid judgments while preserving idempotency.
6. Run focused JUnit and PostgreSQL integration tests, then backend migration validation against a clean test database.

## Task 4: Verify Tutor and learner-twin evidence contracts

**Files:**
- Inspect and only change if a failing contract is found: `services/pythontopia-graph/pythontopia_graph/routes/tutor.py`, Tutor evidence nodes/tests, `backend/src/main/java/com/pythontopia/learner/twin/WeeklyReportService.java`, `frontend/src/components/companion/TwinWeekly.vue`.
- Update: `docs/plans/2026-10-12-admin-area-worklog.md` if it still claims the admin area has only two pages.

1. Add or run tests that confirm image+text Tutor inputs reach the existing AutoGen Tutor workflow and preserve the user question/context through evidence assembly and the critique stages.
2. Verify the twin uses only the current user's authoritative event/mastery/memory/resource data; keep heuristic confidence labeled as evidence sufficiency, not probability.
3. Review admin list search, sort, pagination, status actions, and role guards against current backend DTO fields; test current real pages without copying unsupported Alethicode APIs or classroom schema.
4. Update stale worklog statements with current evidence and limitations; do not add placeholder routes, invented fields, or fake records.
5. Run focused Graph, backend, and Web tests for every changed behavior.

## Task 5: Security review and functional verification

1. Review reachable Web API boundaries for authentication/authorization, owner scoping, upload validation, sandbox dispatch, proxy/SSRF behavior, secrets/logging, and idempotency. Record each finding as confirmed, not reproduced, or not assessed; assign P0/P1/P2 only with evidence.
2. Recheck `services/pythontopia-graph` Piston client settings and Java fallback sandbox; document the exact deployment boundary needed to verify runtime isolation.
3. Run the affected test suites, then broader Web functional tests and lint/type checks for touched areas. Do not run performance benchmarks or make live DeepSeek calls yet.
4. Update `docs/project/CHANGELOG.md` and a dated worklog with changes, test commands/results, remaining unverified boundaries, and competition-demo impact.
5. Keep performance as the final phase; only then consider the separately authorized cap of up to 30 live DeepSeek requests.

## Execution status — 2026-10-13

### Completed in this worktree

- Task 1: scoped Bilibili proxy settings and covered process proxy isolation with Graph regressions.
- Task 2: server-owned expedition node/KC/kind/difficulty/stage policy; adaptation reads saved run state, and the Web trusts the server-issued difficulty.
- Task 3 settlement portion: V209 binds verified attempts to node/phase, limits each node/phase to three unique judged problems, and awards score/depth only once per node/phase.
- Task 3 issuance cap: V210 reserves each new run-bound Graph generation before direct or prefetch execution, limits each node/phase to three reservations and each run to 36, and releases only work rejected before an async task starts. Historical bound rows are conservatively backfilled; direct failures that never persisted a row cannot be reconstructed.
- Task 4 review: existing Tutor image path, fixed AutoGen review stages, evidence-backed weekly learner twin, and three real admin pages were checked. No unsupported admin APIs or forecast fields were added. The earlier admin worklog snapshot wording is clarified.
- Additional Web fixes: failed admin filters clear stale user/review actions; classroom 403 hides protected navigation and write actions; teacher member/quest tables have keyboard-accessible horizontal scrolling; live classroom streaming shows HTTP/network errors and retry state.
- Upload quota preflight now rejects exhausted accounts before parser work, then repeats the quota check under the user-row lock before insert to retain concurrent-upload safety.
- Tutor daily request ceiling: V211 plus an atomic PostgreSQL upsert shares a 30 top-level model-request budget per user per Asia/Shanghai calendar day across normal Q&A and workflow cards. Completed idempotent replay is free; rejected requests that never reach Graph are not charged. A started dispatch reserves a slot even if it then fails.
- Expedition judgment completion: a PostgreSQL fault-injection regression reproduced a stranded verified-attempt row when learning-event publication failed after score persistence. `ExpeditionAttemptCompletionService` now wraps the verified-attempt ledger, idempotent response, and synchronous learning-event listeners in one transaction; the ledger and response roll back together on failure.
- Admin presentation: user/review toolbars and confirmation dialogs now share the existing Alethicode-aligned panel and dialog styling; account status confirmation has Escape/focus recovery and retry coverage. No unsupported admin routes or fabricated records were added.
- Functional verification: full Vitest, TypeScript, production build, changed-file ESLint, 79 focused Tutor/provider/video pytest cases, Ruff, and 112 selected backend JUnit/PostgreSQL cases passed; the empty database migration reached V209.

### Still open

- The per-node/per-phase cap covers new server-run-bound Graph generation requests, not total model spend: ordinary Tutor, Workshop, non-expedition generation, or internal model subcalls are outside it. Historical failed direct-generation calls that never persisted a row are not recoverable from legacy data.
- The 30/day ceiling limits top-level Tutor turns, not tokens, internal multi-agent subcalls, or billed currency. It reduces conversation-ID rotation bypass, but does not guarantee an exact monetary cap.
- Admin logged-in browser interactions were verified in an isolated full stack: user status disable/enable, dialog Escape/focus restoration, and review queue loading all passed with no browser exceptions or API 5xx. Deployment-level Piston isolation remains unverified.
- The security review did not cover every Web endpoint adversarially; no P0 was confirmed within the reviewed surfaces.
- Account-level Tutor request limiting is now implemented; exact token/USD billing caps and broader limits for Workshop or other AI routes remain outside this change.
- CORS deployment values, production Piston isolation, and exception-stack redaction against sensitive upstream sentinels were not runtime-verified. Upload owner scoping and bounded parser limits were reviewed; account quota is now preflighted and atomically rechecked before persistence.
- No missing Web asset was identified, so no new material was downloaded.
- Per user sequencing, keep performance last. Do not report expedition performance metrics until that phase is explicitly run; live DeepSeek request count remains zero in this functional pass.

## Additional functional work — 2026-10-13

- Verification after the audit fixes: Frontend focused Vitest (`ClassroomDetail`, admin users, admin reviews, rogue run store) passed 76 tests across four files; `npm run typecheck`, changed-file ESLint, and `npm run build` passed. The production build prints existing Pyodide `node:*` externalization and `eval` warnings.
- Backend remote-expedition tests passed 76 tests across `BossServiceTest`, `ExpeditionScoreServiceTest`, and `ExpeditionRunPostgresIntegrationTest`; the empty Testcontainers PostgreSQL database migrated through V210.
- V210 legacy backfill test migrated a database to V209, seeded bound normal/boss rows, upgraded to V210, and verified three normal-node reservations, separate boss-phase reservations, and phase inference.
- Upload preflight RED/GREEN: `UploadServiceTest#checksStorageQuotaBeforeParsingTheFile` first failed because the parser ran before quota rejection; after adding a read-only quota preflight plus the existing locked recheck, `UploadServiceTest` passed 17 tests.
- Tutor daily budget RED/GREEN: added a PostgreSQL-backed 30-request per-account/day ceiling shared by both Tutor entry points; idempotent workflow replay is not double charged, and budget rejection marks its claimed workflow run failed before Graph dispatch. Integration coverage verifies account scoping and that 60 concurrent attempts produce exactly 30 reservations.
- V210 upgrade regression caught and corrected a missing projection of `created_at`/legacy row ordering fields in the backfill CTE. Empty-database migration now reaches V211; V209-to-V211 upgrade plus the capped legacy backfill passes.
- Combined selected backend regression passed 168 tests across Tutor, expedition, upload, migration, and PostgreSQL integration classes. Frontend focused Vitest (76), typecheck, changed-file ESLint, and production build remain green from the earlier current worktree run. A Vite-only Playwright smoke verified `/admin/users` redirects to `/login` with no browser page errors; it did not exercise authenticated API interactions.
- Fresh Graph Tutor/provider/vision/attachment/workflow/video regression passed 117 tests (one existing pytest asyncio-mark warning); Ruff passed for the changed proxy route. No live model or Piston request was made.
- Still not run: all Web/Graph/RAG/Maven suites as one current snapshot, authenticated admin/browser walkthrough, deployment CORS/Piston checks, real DeepSeek text/image calls, and expedition performance. The 30/day limit is not a token or monetary cap. Performance remains last; rotate the key shared in chat before any live model call.

## Follow-up verification — Admin and expedition attempt atomicity — 2026-10-13

- Admin users/reviews now share the panel header toolbar; account status uses a themed native dialog with cancel, Escape and focus restoration, and preserves retry on failure. Admin-focused 25 Vitest tests passed; the full frontend Vitest command exited 0, along with typecheck, targeted ESLint and production build.
- Added `ExpeditionAttemptCompletionService` so a judged expedition attempt, its idempotent response, and synchronous learning event handling share a transaction. Before the change, a PostgreSQL fault-injection test failed as expected: event publication raised an exception while one verified attempt remained. After the change, `BossServiceTest`, completion-service tests and `ExpeditionRunPostgresIntegrationTest` passed 70 cases, including rollback of both rows; the empty Testcontainers database migrated through V211.
- `git diff --check` passed. No live DeepSeek/Piston call, deployment CORS/Piston check or expedition performance run occurred.

## Follow-up verification — Full-stack admin browser and backend reruns — 2026-10-13

- Isolated Compose full stack: Graph and RAG health checks returned `ok`; the backend reported `not_ready` because Piston was not configured. Authenticated Playwright checks passed for `/admin/users` (status update and keyboard/focus behavior) and `/admin/problem-reviews` (real empty queue), with zero browser exceptions and API 5xx. The temporary account, database volume, generated local `.env`, and services were removed.
- The first full Maven run reported 1 randomized expedition chest fixture failure and 9 test errors. After making the chest fixture deterministic, `ExpeditionRunPostgresIntegrationTest` passed all 28 tests. `PyodideSchemaTest` had pinned Flyway at V116 while booting application services that require later tables; the test now migrates the current schema and passes 2 tests. The remaining 7 initially errored classes passed individually (19 tests total).
- A single full Maven run has not passed on the corrected snapshot; the per-class reruns do not replace a green full-suite run. No DeepSeek or Piston requests were made. Performance stays pending until a real Piston judge and a rotated API key are available.
