# Pythontopia Security, Expedition, Twin, and Web Quality Plan

> **For implementation agents:** Follow the repository's TDD and security review rules. Keep the current Web-first scope and do not push to GitHub or operate ECS.

**Goal:** Close the verified security and learning-flow defects in the attached delivery brief, then verify the existing DeepSeek Tutor, expedition, learner twin, and Web administration against their real contracts.

**Architecture:** Reuse existing Spring services, learning events, KC mastery/review state, Graph AutoGen runtime, Phaser singleton, admin components, and database tables. Add a migration or service boundary only when an audited failure cannot be corrected in the existing layer. Keep all learner-facing learning evidence tied to server-verified events.

**Tech Stack:** Java 21 / Spring Boot / PostgreSQL, Python 3.11–3.12 / FastAPI / LangGraph / AutoGen, Vue 3 / strict TypeScript / Phaser 3 / Vitest / Playwright.

---

## Scope decisions

- DeepSeek is the only external model provider for AI inference; TTS and ASR remain disabled. Local embeddings, deterministic algorithms, and Python judging are not model-provider requests.
- Web student, teacher, and admin flows are in scope. Android, Windows, WeChat clients, GitHub publishing, and ECS are out of scope per the later user instructions.
- Prefer existing database schema and APIs. Preserve data validation, ownership checks, answer hiding, course evidence, and server-authoritative expedition settlement.
- Do not make performance claims until functional/security work has passed. Do not spend additional DeepSeek requests unless a specific verification gap requires one; keep within the user's 30-request ceiling.

## Research findings and design choices

- Retrieval practice and distributed practice are rated high-utility across learner groups and task types by Dunlosky et al.; use verified practice and scheduled review as the learning loop, and measure delayed outcomes before claiming effectiveness: [APS review](https://www.psychologicalscience.org/journals/pspi/1529100612453266/).
- FSRS represents memory state through stability and difficulty and derives retrievability from review history. Reuse the existing FSRS-lite review service rather than adding another scheduler: [FSRS algorithm reference](https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm).
- Bayesian Knowledge Tracing offers an interpretable probability state per skill, but an online BKT rollout needs calibration evidence; keep it as a later offline comparison rather than replacing verified KC mastery now: [Corbett & Anderson (1995)](https://perso.liris.cnrs.fr/pierre-antoine.champin/2014/m2iade-ia2/_static/893CorbettAnderson1995.pdf).
- A 2026 systematic review of educational digital twins reports inconsistent definitions, mostly partial/unidirectional implementations, and few validated learning-outcome instruments. The first release should therefore be a synchronized, evidence-backed learner state with explainable next actions, not a predictive simulation: [Frontiers review](https://www.frontiersin.org/journals/education/articles/10.3389/feduc.2026.1955212/abstract).
- Slay the Spire's official rulebook documents branching route choice and persistent encounter rewards; borrow route legibility and meaningful trade-offs only. Those mechanics are game-design references, not evidence of educational benefit: [official rulebook](https://cdn.svc.asmodee.net/production-asmodeeca/uploads/2025/12/Slay_the_Spire_-_Rulebook_v2.30_Reprint_Web.pdf).
- For responsive administration pages, WCAG 2.2 Reflow sets a useful check at 320 CSS px without page-level two-dimensional scrolling; the admin page was checked at 320 px as well as desktop/mobile widths: [W3C understanding SC 1.4.10](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html).
- Keep credential handling out of process arguments, which Linux exposes through `/proc/<pid>/cmdline`; OWASP also notes environment variables can be visible to processes and logs, so production should move long-lived secrets to a managed secret store: [Linux proc(5)](https://man7.org/linux/man-pages/man5/proc_pid_cmdline.5.html), [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html).
- Naming review follows the existing Java, Vue, TypeScript, and Python conventions; the typescript-eslint guidance supports a consistent convention but cautions against rules that produce excessive false positives, so no repository-wide naming rule or broad rename was added: [typescript-eslint naming-convention](https://typescript-eslint.io/rules/naming-convention/).

## Execution phases

## Current execution status (2026-10-14)

- **Completed before the current follow-up:** local checkpoint commit `2c8dab6b`; DeepSeek provider/Tutor routing and image privacy hardening; fixed-stage AutoGen Tutor with V199 session-scoped checkpoints; trusted Twin and shared expedition selection changes; boss Phaser scene, CC0 impact sound and license records; admin layout and interaction repairs; Web code-style and accessibility cleanup. Earlier logs contain detailed per-phase tests and measured local run-creation latency.
- **Security audit follow-up:** no P0 or locally reproducible code-level P1 was confirmed. Fresh targeted Maven checks (140 tests), Graph evaluator/self-tester checks (37 tests), Compose sandbox contract checks, and Piston/expedition Postgres integration tests passed. Production Piston network ACL/token enforcement and production runner isolation remain an external release gate; local Compose binds Piston to localhost and denies outbound sockets. The disabled media executor can leave a pending job if its queue rejects work; this is a P2 issue on a currently disabled path. No broad controller-by-controller penetration test or namespace escape exercise was performed.
- **Tutor/Alethicode comparison:** Pythontopia already uses the same fixed-stage AutoGen approach and pinned AutoGen packages. Keep its API/SSE, V199 session checkpoint, private attachment, and event DTO contracts; do not copy Alethicode's dirty/uncommitted V124, Redis bus, or legacy Spring API. Alethicode HEAD is 2026-05-20 with 586 tracked modifications and 449 untracked files, so its working tree was treated as comparison material, not a stable release. Relevant Codex records and exact source mapping are in `2026-10-12-tutor-autogen-worklog.md`.
- **Admin comparison:** the Pythontopia shell matches the current Alethicode visual conventions used for the relevant screens: 240px sidebar, 15px navigation, pale blue-gray shell, white panels, blue-gray tables, and shared search/action areas. Its existing `AdminPanel`, `AdminTable`, and `AdminPagination` keep the matching structure while retaining Pythontopia's role guards and API DTOs; unrelated Alethicode menus have no equivalent API/data here. User and operations pages had browser coverage. The problem-review page previously lacked page-level coverage; this follow-up adds real-API-contract tests for search/filter, audited decision/version payload, and stale-row removal after reload failure.
- **Fresh learner expedition probe:** the local course data has a linear KC prerequisite chain. A newly registered account passed public course problem 476 for `PY43-KC-016` through Piston, but the fifth-chapter run request still returned 422: the rule correctly withheld KC016 until KC001–015 had evidence, then the playable-question filter removed the unassessed KC001 fallback because it has no course coding item. The Workshop micro-check path is the existing legitimate way to build evidence for these early KCs; its resource creation depends on Graph, so that progression was not browser-verified while Graph was stopped. The API error text now explains the prerequisite/check route instead of claiming the whole chapter has no questions. The three exact temporary `codexboss` accounts created for this probe were deleted.
- **DeepSeek request accounting:** the real text and synthetic-image Tutor/workflow checks returned usable text. Fresh Graph log counting shows 32 HTTP 200 completion requests: 4 earlier Tutor/workflow responses and 28 later expedition pre-generation responses. The earlier Tutor record had 8 attempts total, including 4 failed before an HTTP response, so the total attempt count is about 36 and exceeds the 30-call ceiling. This is local request evidence, not the vendor's token or billing breakdown. Graph remains stopped; no more model requests until usage is reconciled. The key was not printed or committed.
- **Remaining:** complete a real judged multi-phase final Boss browser journey; reconcile provider usage/billing; verify production Piston network isolation; obtain a stable rerun for Docker/Testcontainers-limited full Maven cases; and measure Twin A/B plus delayed outcomes. The current browser admin API session is still not live-account verified. No new performance sample was taken for this admin-only regression.
- **Performance:** Final local Vite → Spring sampling was run after stopping Graph: 2 warmups + 12 timed run creations, P50 37.80 ms / nearest-rank P95 65.70 ms, with 22–29 nodes. All 14 runs were deleted (HTTP 204), and a final active-run query returned 204. The two earlier rounds while Graph was live had P50 48.25 / 43.70 ms and P95 558.20 / 2132.90 ms; concurrent model pre-generation may have affected those noisy tails. The direct run-creation route is deterministic and did not invoke Graph. These local samples do not cover question generation, judged submission, or production load.
- **Provider limit incident:** I incorrectly relied on the earlier assumption that Graph was unavailable. Counting stopped-container logs now shows 32 HTTP 200 `api.deepseek.com/chat/completions` responses: 4 in the earlier Tutor/workflow window and 28 during expedition pre-generation. Four other Tutor/provider attempts failed before an HTTP response, for about 36 attempts total. The 30-request ceiling was exceeded by at least 2 successful responses. The Graph container has been stopped; do not resume model-backed testing until account usage is reconciled. Local logs do not confirm vendor tokens or billing.
- **Pending:** A complete real final Boss solve journey; Twin A/B and delayed-outcome measurement; production Piston and public/ECS validation; a stable Docker/PostgreSQL rerun of the environment-limited full Maven cases; reconciling DeepSeek usage/billing. API probes confirmed chapter 1 has no static course coding question and chapter 5 can create/clean a real map. The fresh-account KC016 pass did not satisfy its KC001–015 prerequisites; the corrected 422 now explains the available workshop/check route. The three exact temporary accounts created in this probe were deleted. The admin browser layout used mocked API responses and does not verify a live admin session. No Android, Windows, WeChat, ECS, or GitHub work is in scope.
- **Latest Web verification:** `npm run test` reports 269 files / 2546 tests passed; `npm run typecheck` and `npm run build` exit successfully. Full ESLint reports 0 errors and 7 warnings in two unchanged components and the account-isolation test. Tutor trace tests mock `runTrace`; real browser Pyodide loading was not smoke-tested. No additional DeepSeek request was sent, and no new performance sample was taken because these final changes were frontend-only.

### Phase 0 — Establish the current baseline

- Keep a local commit before further implementation; current anchor: `2c8dab6b` (2026-10-14 DeepSeek Tutor, security, and admin checkpoint).
- Inventory current security contracts, model-provider allowlists, Tutor routes, expedition state machine, Twin APIs, and every Web admin page before editing.
- Save audit findings and evidence in the project changelog/worklogs; treat Alethicode's current dirty worktree as comparison input, not as a source to modify.

### Phase 1 — P0 security and trust boundaries

- Audit registration roles/invites, disabled-account enforcement, JWT revocation, ownership/IDOR, media visibility, uploads, CORS/SSRF, sensitive logs/configuration, and rate limits.
- Audit the Graph evaluator/self-tester and Java subprocess fallback for OS-level isolation, user identity, network/filesystem access, time/memory/process limits, and fail-closed behavior. Do not accept regex blacklists as a sandbox.
- Confirm expedition score, depth, HP, relics, rewards, and completion are derived from persisted server settlements; test replay, cross-user IDs, invalid transitions, duplicate events, and interruption recovery.
- For each confirmed finding, write a failing regression first, fix the root trust boundary, then run the smallest integration suite covering it.

### Phase 2 — DeepSeek Tutor and AI provider parity

- Trace every runtime AI call, background job, and evaluation script. Ensure model inference uses the DeepSeek provider, image-capable routes use the configured vision model, no request accepts an untrusted model override, and TTS/ASR are unreachable.
- Compare Pythontopia's AutoGen Tutor implementation and persisted event lifecycle with Alethicode's latest implementation and relevant Codex logs. Merge only the missing required lifecycle behavior while keeping Pythontopia's current API/SSE/image privacy contract.
- Verify with provider routing tests and real text/image requests. Earlier Tutor verification made 8 attempts (including exploratory low-token probes and one connection timeout); the text stream, image stream, and workflow structured-image request returned usable results. Subsequent stopped-container log review confirmed 32 HTTP 200 completion responses: four earlier Tutor/workflow responses and 28 accidental expedition pre-generation responses; four other Tutor attempts failed before an HTTP response. This is about 36 attempts and exceeds the 30-request ceiling. The Graph container is stopped, and no further model request will be made until vendor usage is reconciled. Prompts used synthetic content; no key value was logged or committed.

### Phase 3 — Expedition continuity and adaptive practice

- Audit `EndlessExpeditionPage`, `rogueRun`, `ExpeditionRunService`, `BossService`, pre-generation, graph selection, cache, judge, settlement, and resume paths.
- Preserve immediate ready-question reuse, bounded pre-generation, explicit timeout/retry/cancel state, same-KC Golden fallback, and server-owned rewards. Fix confirmed stalls, repeats, or stuck phases with regression tests.
- Keep the tower finite and explicit unless a verified product contract requires an infinite mode; make route/relic choices explainable in learning terms.
- Improve boss feedback only from settled server results; every failed path uses actual server HP and has an exit/retry action.

### Phase 4 — Trusted learner twin

- Reconcile learner profile, mastery, evidence, review state, and weekly report through existing services; unknown KCs remain unassessed and self-report cannot mint objective evidence.
- Use one deterministic, explainable policy for recommendations and expedition. Verify idempotent evidence projection and event recovery.
- Keep the twin page centered on evidence, confidence, real growth, and navigable next actions; mark speculative prediction as unavailable until offline calibration and delayed-outcome evaluation exist.

### Phase 5 — Web administration and interaction audit

- Compare all real Pythontopia admin pages, responsive table/list behavior, typography, colors, search/filter/sort/pagination, mutations, empty/error/loading states, and keyboard flow with current Alethicode components.
- Reuse `AdminTable`, `AdminPagination`, `AdminPanel`, current API DTOs, and role checks. Do not add placeholder menus or fake APIs.
- Run page-level tests for visible actions and at least one browser viewport check for desktop and narrow screens.

### Phase 6 — Functional verification, then local performance

- Run targeted Java/PostgreSQL, Graph/RAG, Web typecheck/lint/tests/build, and Playwright journeys for the changed flows. Record environment failures separately from assertion failures; do not claim a full suite pass when it failed to start.
- After functional checks, benchmark only local Web/API paths that are safe to run without model calls. Report P50/P95 with sample count, cache state, and exclusions; ECS is excluded.
- Final delivery lists verified fixes, source-backed design choices, changed files, test evidence, unverified behavior, and residual risks.
