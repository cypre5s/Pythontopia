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

- **Completed:** Phase 0 baseline and local checkpoint commit `c969d142`; Phase 2 provider/Tutor source audit plus real DeepSeek requests through Tutor text/image streams and the workflow structured-image path; Phase 5 admin interaction repairs, responsive browser checks, and a remote Markdown image privacy fix. Credential scripts no longer accept or expose API keys in command arguments, and RAG index initialization fails closed without a valid configured token. Media jobs fail before enqueue when image generation is disabled; disabled TTS creates an explicitly silent result, while enabled TTS errors fail the job. Current Web verification: Vitest 269 files / 2546 tests, typecheck, production build, Graph 1826 passed / 1 skipped; ESLint has 0 errors and 7 existing warnings. Account-isolation and teacher/student Playwright journeys passed. This continuation confirmed registration → expedition map (25 nodes) → Golden-backed question rendering without browser errors; the prefetch was PENDING for about 8 seconds, then READY after the retry. Tutor `EXECUTION_TRACE` now uses existing browser Pyodide, preserves sample input exactly, and is explicitly excluded from judging and mastery updates; admin review links are allowlisted and sidebar badges are semantic SVG icons.
- **Provider hardening added:** the research lab's shared outbound client now enforces the same DeepSeek host/model allowlist as Graph, refuses redirects, and its live CLIs no longer take API keys in argv. Offline tests cover these constraints. This does not replace the still-pending request/billing reconciliation and no additional model calls were made.
- **Partially complete:** Phase 1 focused trust-boundary and sandbox checks passed; targeted media Maven tests passed, but the latest full Maven run had 1369 tests, 0 assertion failures, and 7 environment errors: 6 Docker/Testcontainers host-port startup conflicts and 1 PostgreSQL connection reset in the long-term simulation. Sequential isolation still hit three Testcontainers host-port conflicts. Graph's guarded public-document browser request remains blocked because allowed domains resolve to non-public IPs in this environment; the guard failed closed. Phase 3 focused selection, Boss, recovery, Phaser, and browser-settled-state checks exist, but the full real judged final Boss journey remains unverified. Phase 4 trusted Twin changes and focused tests are complete; calibrated learning-outcome comparison remains unverified.
- **Performance:** Final local Vite → Spring sampling was run after stopping Graph: 2 warmups + 12 timed run creations, P50 37.80 ms / nearest-rank P95 65.70 ms, with 22–29 nodes. All 14 runs were deleted (HTTP 204), and a final active-run query returned 204. The two earlier rounds while Graph was live had P50 48.25 / 43.70 ms and P95 558.20 / 2132.90 ms; concurrent model pre-generation may have affected those noisy tails. The direct run-creation route is deterministic and did not invoke Graph. These local samples do not cover question generation, judged submission, or production load.
- **Provider limit incident:** I incorrectly relied on the earlier assumption that Graph was unavailable. It was healthy on `localhost:18091`; this continuation's expedition prefetches produced 28 successful `POST /chat/completions` events in local Graph logs. Combined with the 8 previously recorded provider attempts, the total may be 36, above the user's 30-request ceiling. The Graph container has been stopped; do not resume model-backed testing until provider usage is reconciled. Local logs confirm request events, not vendor billing/token totals.
- **Pending:** A complete real final Boss solve journey; Twin A/B and delayed-outcome measurement; production Piston and public/ECS validation; a stable Docker/PostgreSQL rerun of the environment-limited Maven cases; profiling the noisy map-creation tail latency; reconciling the DeepSeek request total with provider usage. An API probe confirmed chapter 1 correctly returns `EXPEDITION_NO_PRACTICE_PROBLEMS`; chapter 5 with `PY43-KC-041` created and cleaned a real map. The admin browser layout used mocked API responses, so it does not verify a live admin account session. This turn's browser-created test accounts remain in the local development database. No Android, Windows, WeChat, ECS, or GitHub work is in scope.
- **Latest Web verification:** `npm run test` reports 269 files / 2546 tests passed; `npm run typecheck` and `npm run build` exit successfully. Full ESLint reports 0 errors and 7 warnings in two unchanged components and the account-isolation test. Tutor trace tests mock `runTrace`; real browser Pyodide loading was not smoke-tested. No additional DeepSeek request was sent, and no new performance sample was taken because these final changes were frontend-only.

### Phase 0 — Establish the current baseline

- Keep a local commit before further implementation; current anchor: `c969d142` (2026-10-14 Tutor Markdown and admin interaction checkpoint).
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
- Verify with provider routing tests and real text/image requests. Earlier Tutor verification made 8 attempts (including exploratory low-token probes and one connection timeout); the text stream, image stream, and workflow structured-image request returned usable results. In the later continuation, accidental expedition prefetches made 28 successful chat-completion HTTP requests because Graph was live on port 18091. The combined count may exceed the 30-request ceiling; the Graph container is stopped, and provider usage needs reconciliation before any further model call. Prompts used synthetic content; no key value was logged or committed.

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
