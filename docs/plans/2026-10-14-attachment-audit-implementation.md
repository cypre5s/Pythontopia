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

## Execution phases

### Phase 0 — Establish the current baseline

- Keep a local commit before further implementation; current anchor: `a82fa3bf`.
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
- Verify with provider routing tests and prior real text/image evidence. A real API request is reserved for an identified gap and must remain within the request ceiling.

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
