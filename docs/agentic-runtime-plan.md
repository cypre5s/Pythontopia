# Pythontopia Agentic Runtime Plan

> Created: 2026-06-30
> Source inputs: `README.md`, `AGENTS.md`, `docs/clients/api-contract.md`, current backend/frontend/graph/rag checkout, database migrations, tests, CI, and the pasted Agentic Runtime execution plan.

## Repository State Confirmation

### Current Stack

- Backend: Java 21, Spring Boot 3.3, Maven, Flyway, PostgreSQL, Redis, Spring Security, springdoc OpenAPI.
- Frontend: Vue 3, Vite, TypeScript strict, Pinia, Vitest, Playwright, Phaser 3.
- Graph service: FastAPI, Pydantic v2, LangGraph, structured agent schemas, optional OpenTelemetry/eval/optimization/browser tooling.
- RAG service: FastAPI, pgvector/PostgreSQL access, hybrid retrieval, safety filtering, GraphRAG optional support.
- Data and runtime: `data/course-pack-43/`, Flyway migrations through `V183__loop_run_trace_required.sql`, Docker Compose and root `./dev.sh`.

### Existing Learning and Agent Capabilities

- Course base is fixed to `packId=43` / `Python语言基础`, with course pages, KC, examples, problems, videos, and evidence-page image endpoints.
- Tutor/RAG paths exist through `/api/tutor/ask`, `/rag/query`, and course evidence APIs.
- Workshop and orchestrator paths exist through `/api/workshop/**`, `/api/workshop/resources/stream`, `/api/orchestrator/turn/stream`, and `/api/intent/route`.
- Judge path is real: Pyodide is preview only, while `POST /api/submissions` writes formal service-side results.
- Bug Mine already handles failed submissions and FSRS-lite echoes.
- Mainline/R-15 state is backend-authoritative through `/api/mainline/**`.
- Runtime/eval groundwork exists:
  - `learning_event_log`,
  - `agent_loop_run`,
  - `policy_registry`,
  - `loop_checkpoint`,
  - `agent_run`,
  - `agent_tool_call`,
  - `loop_eval_result`,
  - graph eval gates and golden datasets for prior Agentic M0-M3 work.

### Compatibility Requirements

The following external/client-facing contracts must stay compatible unless a later phase explicitly adds a documented additive extension:

- `POST /api/game/bootstrap`
- `/api/course/**`
- `/api/learner/**`
- `/api/quests/**`
- `/api/submissions/**`
- `/api/bug-mine/**`
- `/api/mainline/**`
- `/api/tutor/ask`
- `/api/orchestrator/turn/stream`
- `/api/workshop/resources/stream`
- `/api/intent/route`
- JWT Bearer and the unified error envelope in `docs/clients/api-contract.md`.

### Suitable Extension Points

- Backend control plane: `backend/src/main/java/com/pythontopia/loop`, `agent/run`, `learner/event`, `learner/evidence`, `workshop`, `bugmine`, `world`, `classroom`, `protocol`.
- Migrations: `backend/src/main/resources/db/migration/`, continuing after `V183`.
- Graph runtime: `services/pythontopia-graph/pythontopia_graph/graph`, `schemas`, `nodes`, `agents`, `eval`, `tools`.
- RAG/tooling: `services/pythontopia-rag/pythontopia_rag/index`, `rag`, `graph`, `safety`, `tools`.
- Frontend surfaces: `frontend/src/api`, `components/workshop`, `components/learning`, `components/teacher`, `stores/learning`, and existing E2E `frontend/e2e/agenticDeepLoop.spec.ts`.
- Docs: this file plus `docs/agentic-runtime-progress.md`, future `docs/loop-runtime.md`, `docs/tool-contracts.md`, `docs/eval-runtime.md`, `docs/world-state-runtime.md`, and `docs/incident-runbook.md`.

### Do Not Break

- Do not reintroduce inventory, currency, shop, or broad economic gameplay into the learning mainline.
- Do not replace `town_hub` as the main R-14/R-15 world entry.
- Do not treat Pyodide preview as formal judge evidence.
- Do not generate or store ungrounded course citations.
- Do not let high-risk or class-wide actions bypass review, feature flag, canary, or rollback.
- Do not add fake frontend data to simulate runtime state.
- Do not enable open-ended ReAct or unbounded tool loops; every loop must have max steps, stop conditions, fallback, trace, and policy version.

## Plan Alignment And Current Reality

The pasted execution plan asks for Phase 0-9. The repository already contains a significant prior Agentic slice, recorded in `docs/plans/2026-06-30-agentic-14-week-completion-worklog.md` and recent commits:

- M0: runtime persistence, contract checker, trace gate.
- M1: learner/debug replay and golden gate.
- M2: resource/world evidence gates and effectiveness signal.
- M3: system evolution gate, canary dry-run scripts, rollback status.

This means the new Phase 0-9 plan should not duplicate those pieces blindly. Instead, each phase must:

- verify what is already real,
- reuse or extend existing implementations,
- fill gaps against the stricter final delivery definition,
- keep the old M0-M3 evidence as implementation history, not as proof that the new Phase 1-9 scope is complete.

## Phase Map

| Phase | Name | Goal | Done When |
|---|---|---|---|
| 0 | Baseline audit and execution docs | Confirm repo state, constraints, plan, changelog and commit rules | `docs/agentic-runtime-plan.md`, `docs/agentic-runtime-progress.md`, changelog entry, verification, local commit |
| 1 | Unified event model and Agent Run control plane | Normalize events, agent runs, tool calls, policy decisions, loop state/API | Migrations, backend DTO/repository/service/controller, tests, compatible public APIs |
| 2 | Loop Runtime skeleton and bounded state machines | Five loop types share structured state, max attempts, stop/fallback, trace | Graph/runtime tests for happy/fail paths across five loops |
| 3 | Student learning loop | Observe, diagnose, teach, practice, reflect, update | Real learning events produce structured diagnosis, intervention, practice, reflection, profile/KC updates |
| 4 | Code debug loop | Fail, hypothesize, hint, resubmit, compare, transfer | Failed submission creates debug session with hypotheses, hints, compare, transfer and memory writeback |
| 5 | Resource generation loop | Goal, generate, critique, revise, publish, evaluate | Artifact lifecycle includes grounding refs, revision, publish state and effectiveness writeback |
| 6 | Game world loop | State, quest, action, reward, world patch, new quest | Learning behavior updates world state/quest graph with non-economic rewards and rollback |
| 7 | System evolution loop | Trace, eval, cluster, improve, deploy, monitor | Regression gates, policy versions, canary, rollback, golden/adversarial/long-running/audit sets |
| 8 | Frontend and teacher operations | Students/teachers can see and govern runtime state | Real API-backed loop panels, trace views, review/rollback/feature flag status |
| 9 | Full-chain testing and Beta handoff | Prove local startup and at least one complete end-to-end Agentic Learning Loop | Backend/graph/rag/frontend/db/API/E2E verification, docs, changelog, final commit |

## Stage Rules

Each phase follows this order:

1. Phase analysis: read relevant current code and identify changed directories/interfaces.
2. Phase plan: split into 3-8 concrete objectives with done definitions.
3. Implementation: real code or docs, no placeholder core logic.
4. Tests: add or update focused coverage for new behavior.
5. Verification: run the relevant commands and record results.
6. Docs: update progress and any domain docs.
7. Changelog: add the phase entry.
8. Self-check: `git status`, `git diff`, `git diff --check`, relevant tests.
9. Local commit: stage only phase-owned files.

## Verification Matrix

Use the smallest command set that proves the phase, and broaden when shared behavior is touched.

| Area | Commands |
|---|---|
| Backend compile | `cd backend && mvn --settings .mvn/settings.build.xml -q -DskipTests test-compile` |
| Backend tests | `cd backend && mvn --settings .mvn/settings.build.xml -q -Dtest=<focused tests> test` |
| Graph tests | `cd services/pythontopia-graph && python3 -m pytest -q <tests>` |
| RAG tests | `cd services/pythontopia-rag && python3 -m pytest -q <tests>` |
| Frontend typecheck | `cd frontend && npm run typecheck` |
| Frontend unit tests | `cd frontend && npm run test -- --run <tests>` |
| Browser acceptance | `cd frontend && PYT_E2E_REGISTER=1 npx playwright test e2e/agenticDeepLoop.spec.ts --retries=0` |
| Local startup | `./dev.sh`, then `/healthz` checks for frontend/backend/graph/rag |
| Static diff | `git diff --check` and `git diff --cached --check` |

## Commit Rules

- One phase may contain multiple commits, but must end with a summary commit.
- Preferred messages:
  - `feat(agentic-runtime): complete phase N - <name>`
  - `test(agentic-runtime): add phase N coverage`
  - `docs(agentic-runtime): update phase N progress`
- Because the current worktree has unrelated dirty files, stage only files owned by the active phase.

## Known Risks

- Existing unrelated dirty files in licenses, guide screenshots, arena/boss files, and presentation artifacts must remain unstaged.
- Full backend `mvn test` can be slow and has had Testcontainers flakiness; focused Testcontainers coverage plus compile is required per phase, with broader runs when feasible.
- Real online LLM/provider evaluation is not a local CI gate yet; current eval/canary paths are offline or dry-run unless explicitly enabled.
- The new Phase 1-9 scope is stricter than the prior M0-M3 work. Prior work is useful baseline evidence, not an automatic completion claim for the new phases.
