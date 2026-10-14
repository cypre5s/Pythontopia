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

## Phase 1 Implementation Notes

- `/api/events` is backed by the existing `learning_event_log` table, but accepts only low-trust browser feedback (`quiz_finished`, `answer_feedback`). `source=frontend` and importance are assigned by the server; verified practice outcomes must come from server-side domain events, never this client endpoint.
- `/api/agent-runs` reuses the existing `loop_run` service and table. The older `/api/loop/runs` endpoint remains compatible.
- `V184__agent_runtime_control_plane.sql` adds only the missing Phase 1 control-plane tables:
  - `loop_state`,
  - `policy_decision`.
- `loop_state` and `policy_decision` are owner-scoped through `LoopRuntimeService`, so cross-user reads/writes fail through the same hidden-existence rule used by `loop_run`.
- The initial `/api/agent-runs/{id}/stream` endpoint emits AG-UI-compatible lifecycle events from stored run metadata. Later phases may replace this deterministic replay body with live runtime streaming, but the resource path and event names should stay stable.

## Phase 2 Implementation Notes

- `pythontopia_graph.runtime.loop_runtime` is the shared graph-side skeleton for the five loop families. It is intentionally deterministic and contract-first.
- Supported template names are:
  - `student_learning_loop`,
  - `code_debug_loop`,
  - `resource_generation_loop`,
  - `game_world_loop`,
  - `system_evolution_loop`.
- Each template defines:
  - `loop_type`,
  - ordered states,
  - max attempts,
  - fallback action.
- `LoopRuntime.execute()` consumes an `EventEnvelope`, `policy_version`, and `max_steps`, then returns `LoopExecutionResult` with a validated `LoopResult`.
- This is the common state-machine layer for Phase 3-7. Domain-specific agents should deepen template behavior without changing the frozen `LoopResult` output contract.

## Phase 3 Implementation Notes

- `pythontopia_graph.runtime.student_learning_loop` implements the student learner loop as a deterministic graph-side runtime module, not a replacement for Tutor, PathPlanner, ReflectionAgent, or backend learner services.
- The loop consumes:
  - one triggering `EventEnvelope`,
  - recent `EventEnvelope` records,
  - `LearnerProfileState`,
  - `LearnerKcState`,
  - course-bound `CourseEvidence`,
  - `policy_version`.
- It emits the six structured stages required by the pasted plan:
  - `ObservationSnapshot`,
  - `Diagnosis`,
  - `TeachingPlan`,
  - `PracticeAssignment`,
  - `ReflectionPrompt`,
  - `LearnerUpdatePlan`.
- `POST /runtime/student-learning-runs` exposes the structured loop state through the graph service for API-backed UI/status panels. Phase 8 can build the student/teacher display on top of this route instead of introducing fake frontend state.
- Evidence behavior is intentionally strict:
  - missing evidence returns `REJECTED` with `request:learner-evidence`,
  - Pyodide preview pass returns `PARTIAL` with `submit:formal-judge`,
  - formal judge pass returns a positive KC mastery delta,
  - repeated formal failures produce remediation and an error-pattern profile patch,
  - three or more failures stay remedial rather than escalating difficulty.
- The phase returns a frozen-contract `LoopResult` with `loop_type=LEARNER`, `trace_id`, `policy_version`, evidence refs, scores, and next actions. Later backend orchestration may persist the update plan through Phase 1 control-plane resources.

## Phase 4 Implementation Notes

- Existing backend persistence already covers the Debug Session requirement:
  - `practice_submission` and `judge_result` hold formal judge evidence,
  - `debug_case` stores submission-level debug artifacts,
  - `bug_mine_session` and `bug_mine_card` store Bug Mine sessions, hint cards, retry counts, depth, and variant mastery.
- `pythontopia_graph.runtime.code_debug_loop` adds the missing graph-side Debug Runtime contract around those foundations. It does not replace Bug Mine or `DebugAgent`; it turns their inputs and outputs into a recoverable loop result.
- The loop consumes:
  - `DebugSubmissionSnapshot`,
  - optional `ResubmissionSnapshot`,
  - prior failure count,
  - course-bound `DebugCourseEvidence`,
  - `policy_version`,
  - `trace_id`.
- It emits the six structured stages required by the pasted plan:
  - `FailureObservation`,
  - ranked `DebugHypothesis` entries from runtime behavior, code structure, and KC-prerequisite angles,
  - `HintPlan`,
  - `ResubmissionComparison`,
  - `TransferTask`,
  - `DebugMemoryWriteback`.
- `POST /runtime/code-debug-runs` exposes the structured debug loop state through the graph service for API-backed UI/status panels.
- Safety and fallback behavior is intentionally strict:
  - missing debug evidence returns `REJECTED` with `request:debug-evidence`,
  - missing formal resubmission returns `PARTIAL` with `resubmit:formal-judge`,
  - three failed attempts return `fallback:prerequisite-remediation`,
  - successful resubmission returns a transfer task rather than ending at "fixed".
- The phase returns a frozen-contract `LoopResult` with `loop_type=DEBUG`, `trace_id`, `policy_version`, evidence refs, scores, and next actions. Later backend orchestration may persist the memory writeback through existing learner/profile and Bug Mine event listeners.

## Phase 5 Implementation Notes

- Existing `ResourceGraph` already covers the generation/critique mechanics required by Phase 5:
  - evidence retrieval,
  - resource-agent design,
  - grounding critic,
  - hallucination detector,
  - semantic grounding,
  - safety critic,
  - bounded revision retries and oscillation detection,
  - publish decision,
  - evaluation hook.
- `pythontopia_graph.runtime.resource_lifecycle_loop` adds the missing artifact lifecycle contract around that graph response. It consumes `ResourceProduceResponse` rather than duplicating the LLM-generation path.
- The loop consumes:
  - `ResourceGoal`,
  - `ResourceProduceResponse`,
  - optional `revision_of`,
  - optional `ArtifactEffectivenessSnapshot`,
  - `policy_version`,
  - `trace_id`.
- It emits the six structured stages required by the pasted plan:
  - `NormalizedResourceGoal`,
  - `ArtifactManifest`,
  - `CritiqueReport`,
  - `RevisionPlan`,
  - `PublishState`,
  - `EvaluationResult`.
- `POST /runtime/resource-lifecycle-runs` exposes the structured artifact lifecycle through the graph service for API-backed UI/status panels.
- Lifecycle decisions are evidence-bound:
  - publish only when graph publish decision is `PUBLISH`,
  - hold for review when evidence is missing or unbound,
  - reject when grounding/safety critic fails,
  - retain `revision_of` for revised artifacts,
  - request revision when effectiveness snapshots show low adoption, low micro-check pass rate, or negative mastery delta.
- The phase returns a frozen-contract `LoopResult` with `loop_type=RESOURCE`, `trace_id`, `policy_version`, evidence refs, artifact manifest, scores, and next actions. Scheduled 24h/7d evaluation jobs can later feed `ArtifactEffectivenessSnapshot` through existing workshop effectiveness APIs.

## Phase 6 Implementation Notes

- Existing M2 work already defines the world update contract through `WorldLoopProposal`, including evidence-bound state patches, quest proposals, NPC dialogue, and reward plan. Phase 6 reuses that contract rather than adding a parallel world schema.
- `pythontopia_graph.runtime.world_state_loop` implements the game world loop as a deterministic graph-side runtime module, not a Phaser renderer or backend persistence replacement.
- The loop consumes:
  - `LearnerWorldSnapshot`,
  - `WorldQuestHistory`,
  - `LearningActionEvidence`,
  - optional previous world-state values,
  - reward preferences,
  - `policy_version`,
  - `trace_id`.
- It emits the six structured stages required by the pasted plan:
  - `WorldStateSnapshot`,
  - `QuestGraphUpdate`,
  - `WorldActionResult`,
  - `NarrativeReward`,
  - `WorldLoopProposal`,
  - `RollbackPatch`.
- `POST /runtime/world-state-runs` exposes structured world-loop state through the graph service for API-backed UI/status panels.
- R-14/R-15 constraints are hard-coded into the runtime contract:
  - output world state is normalized to `town_hub`,
  - all generated patch keys use `town_hub.*`,
  - inventory/currency/shop/coin/gold reward requests are rejected,
  - no farm/crop/economy route is used for mainline learning.
- Page-read actions create a formal-submission follow-up quest but do not grant positive mastery delta. Formal judge passes can grant small evidence-bound mastery progress plus non-economic narrative rewards.
- The phase returns a frozen-contract `LoopResult` with `loop_type=WORLD`, `trace_id`, `policy_version`, evidence refs, proposal artifact, rollback patches, scores, and next actions. Later backend/world orchestration can persist or apply the proposal after review.

## Phase 7 Implementation Notes

- Existing M3 work already owns the offline system-evolution mechanics:
  - stable eval dataset loading,
  - failure clustering,
  - one-variable `PolicyCandidate`,
  - offline replay gate,
  - 5% canary dry-run plan,
  - rollback status,
  - local scripts for offline eval, nightly improve, and canary dry-run.
- `pythontopia_graph.runtime.system_evolution_loop` adds the missing runtime/status contract around that M3 baseline. It does not enable real traffic automatically.
- The loop consumes:
  - `EvalDatasetItem` records,
  - required eval-suite results,
  - `MonitorSnapshot`,
  - `policy_version`,
  - `trace_id`,
  - `dry_run`.
- It emits the six structured stages required by the pasted plan:
  - `TraceSummary`,
  - `EvaluationGate`,
  - M3 `FailureCluster` list,
  - M3 `PolicyCandidate`,
  - `DeploymentPlan`,
  - `MonitorDecision`.
- `POST /runtime/system-evolution-runs` exposes structured policy-evolution state through the graph service for API-backed operations panels.
- Phase 7 adds stricter deploy gates beyond the M3 dataset:
  - required suite categories are `regression`, `adversarial`, `long_running`, and `audit`,
  - failing or missing suites block deploy,
  - offline replay regression blocks deploy,
  - monitor violations for schema validity, regression rate, cost ratio, or live error rate produce rollback next actions.
- The phase returns a frozen-contract `LoopResult` with `loop_type=IMPROVE`, `trace_id`, `policy_version`, eval trace evidence refs, candidate/deployment artifacts, scores, monitor status, and next actions.

## Phase 8 Implementation Notes

- The frontend should not call graph `/runtime/**` directly. Vite proxies only `/api`, and Spring Boot remains the auth/client contract boundary.
- Existing student runtime visibility already starts with `AiRuntimeSummaryPanel` and `/api/ai/runtime/summary`. Phase 8 adds operations/governance state without weakening that student-only endpoint.
- `GET /api/ai/runtime/operations` is the shared operations endpoint for student/teacher/admin readers. It derives:
  - five loop operation states: `LEARNER`, `DEBUG`, `RESOURCE`, `WORLD`, `IMPROVE`,
  - governance items: resource review, world patch review, policy canary review.
- Operations status is derived from existing protocol, evidence graph, Evidence Memory, Learner Twin, and browse-safety summaries. Missing evidence remains `INSUFFICIENT_EVIDENCE`; frontend must not turn it into PASS.
- `RuntimeOperationsPanel.vue` is the shared UI surface for:
  - the student workshop call-process drawer,
  - teacher review/governance page.
- The panel is intentionally compact and action-oriented:
  - status chip,
  - loop/governance label,
  - next action,
  - evidence refs.
- Phase 8 does not execute policy promotion, rollback, or world patch application. It exposes API-backed review state so Phase 9/browser acceptance and later operations can verify governance without fake frontend data.

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
