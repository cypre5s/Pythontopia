# Agent Runtime, Evidence Memory, Eval Optimization Deepening Design

> Goal: deepen the three AI routes in order: protocolized Agent Runtime, Evidence Memory 2.0, and Eval/Prompt Optimization, without replacing the existing bounded learning workflow or enabling open ReAct loops.

## Context

The current codebase already has several advanced AI foundations:

- AG-UI-like SSE compatibility in `sse_agui.py`, `agui.ts`, and `useSseStream.ts`.
- MCP/A2A-style capability descriptors in `ProtocolGatewayService`.
- SQL-native Learning Evidence Graph over resources, checks, mastery events, RAG traces, pages, and KC relations.
- TutorBench-lite and graph-side eval runners.
- Memory retrieval, reflection, recommendations, and FSRS-like review state.

The next risk is uncontrolled complexity. `WorkshopChat.vue`, `orchestrator_graph.py`, `routes/tutor.py`, `ResourceWorkshopService`, and `LearningEvidenceGraphRepository` are already dense. The deepening should therefore standardize runtime contracts, consolidate memory signals, and make optimization measurable.

## Route 1: Protocolized Agent Runtime

Route 1 makes agent runs auditable and interoperable.

Design:

- Treat AG-UI events as the canonical stream surface.
- Keep legacy event compatibility, but measure and prefer official event names.
- Export MCP-style tools, resources, prompts, AG-UI events, and OpenTelemetry GenAI attributes from the protocol descriptor.
- Add deterministic runtime quality metrics:
  - lifecycle coverage
  - text streaming coverage
  - step trace coverage
  - tool event coverage
  - state sync coverage
  - terminal coverage
  - legacy penalty
- Switch orchestrator streaming to AG-UI official event names while retaining front-end compatibility through `useSseStream`.

Primary module targets:

- `services/pythontopia-graph/pythontopia_graph/sse_agui.py`
- `services/pythontopia-graph/pythontopia_graph/routes/orchestrator.py`
- `frontend/src/composables/agui.ts`
- `frontend/src/composables/useSseStream.ts`
- `backend/src/main/java/com/pythontopia/protocol/*`

## Route 2: Evidence Memory 2.0

Route 2 turns evidence graph signals into a selective, relation-aware learner memory layer.

Design:

- Add a persisted evidence memory snapshot table, derived from the evidence graph.
- Keep snapshots as projections, not as a second source of truth.
- Aggregate direct signals from failed checks, failed practice, passed checks, grounded pages, and check/event ids.
- Propagate weak KC pressure across `course_kc_edge` with decay, inspired by personalized graph diffusion.
- Integrate evidence memory into recommendation ranking and reasons, without replacing mastery and insight signals.
- Preserve traceability by carrying event ids, check ids, page ids, direct score, propagated score, and retention policy.

Primary module targets:

- `backend/src/main/java/com/pythontopia/learner/evidence/*`
- `backend/src/main/java/com/pythontopia/learner/recommendation/LearnerRecommendationService.java`
- `backend/src/main/resources/db/migration/V180__learner_evidence_memory_snapshot.sql`

## Route 3: Eval And Prompt Optimization Factory

Route 3 makes improvements measurable before they become product defaults.

Design:

- Add a deterministic optimizer around existing eval samples and scorers.
- Compare a baseline policy against multiple candidate policies.
- Score candidates by quality mean, uplift, regression count, and reason distribution.
- Accept a candidate only if uplift clears a configured threshold and regressions stay under budget.
- Produce a machine-readable optimization report for future prompt, retrieval, or routing tuning.

Primary module targets:

- `services/pythontopia-graph/pythontopia_graph/eval/optimization.py`
- `services/pythontopia-graph/tests/test_eval_optimization.py`
- `services/pythontopia-graph/pythontopia_graph/eval/datasets/*`

## Non-Goals

- Do not introduce a new graph database.
- Do not replace the existing course RAG with a black-box framework.
- Do not enable open-ended ReAct loops.
- Do not let recommendations rely on untraceable natural language reasons.
- Do not require external LLM calls for CI tests.

## Success Criteria

1. Orchestrator SSE emits AG-UI official lifecycle/text/step/state events and passes compatibility tests.
2. Protocol descriptor exports tools, resources, prompts, events, and OTel GenAI attributes.
3. Runtime quality metrics show canonical AG-UI streams scoring higher than legacy streams.
4. Evidence Memory snapshots persist selective, relation-expanded learner signals.
5. Recommendations can be reranked by evidence memory while preserving trace ids.
6. Evidence Memory quantitative tests show graph-backed memory scoring higher than naive recency-only memory.
7. Eval optimization report selects a candidate only when uplift is measurable and regressions are bounded.
8. Route-specific tests plus impacted full suites pass before completion.
