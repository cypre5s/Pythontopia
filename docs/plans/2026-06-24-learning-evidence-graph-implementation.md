# Learning Evidence Graph Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a SQL-native Learning Evidence Graph that connects RAG evidence, learning events, resource checks, mastery deltas, TutorBench trajectory evaluation, and memory-aware personalization.

**Architecture:** Add a backend `learner.evidence` package that dynamically projects existing PostgreSQL tables into typed nodes, edges, and metrics. Extend learning-effect and TutorBench services to consume graph metrics, then expose evidence-grounded personalization signals without introducing a separate graph database or open-ended ReAct behavior.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, PostgreSQL JSONB/arrays, Flyway, JUnit 5, AssertJ, Testcontainers, existing Vue/TypeScript clients for later UI reuse.

---

### Task 1: Evidence Graph DTOs And Repository RED/GREEN

**Files:**
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/EvidenceNodeDto.java`
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/EvidenceEdgeDto.java`
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/EvidenceGraphMetricsDto.java`
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/LearningEvidenceGraphDto.java`
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/LearningEvidenceGraphRepository.java`
- Test: `backend/src/test/java/com/pythontopia/learner/evidence/LearningEvidenceGraphServiceTest.java`

**Step 1: Write failing test**

Create a Testcontainers test that seeds one user, one resource, one resource check, one mastery event, one learning event, one resource run with page ids, and one `course_kc_edge`. Assert the graph includes RESOURCE/CHECK/KC/PAGE/MASTERY_EVENT nodes and relations `COVERS_KC`, `CHECKS_RESOURCE`, `UPDATES_MASTERY`, `GROUNDED_BY_PAGE`, `KC_RELATION`.

**Step 2: Run RED**

Run:

```bash
cd backend
mvn -s ~/.m2/settings.xml -q -Dtest=LearningEvidenceGraphServiceTest test
```

Expected: compile failure because evidence graph classes do not exist.

**Step 3: Implement DTOs and repository**

Implement repository with SQL reads from existing tables:

- `learning_event_log`
- `learning_resource`
- `resource_learning_check`
- `kc_mastery_event`
- `resource_run`
- `course_kc_edge`

Keep repository read-only and tolerant of missing optional tables/rows.

**Step 4: Run GREEN**

Run the same Maven command. Expected: test passes.

### Task 2: Evidence Graph Metrics And Service

**Files:**
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/LearningEvidenceGraphService.java`
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/LearningEvidenceGraphController.java`
- Modify: `backend/src/test/java/com/pythontopia/learner/evidence/LearningEvidenceGraphServiceTest.java`

**Step 1: Write failing metrics test**

Assert:

- `nodeCount > 0`
- `edgeCount > 0`
- `checkCoverageRate == 1.0`
- `masteryEvidenceRate == 1.0`
- `groundedPageCount >= 1`
- `graphCompletenessScore` is higher than a naive resource-only baseline.

**Step 2: Run RED**

Expected: missing service/metrics behavior.

**Step 3: Implement service and controller**

Expose:

```text
GET /api/learner/evidence-graph?focusKc=PY43-KC-001&limit=80
```

Clamp limit to 1..200. Metrics are deterministic and zero when no evidence exists.

**Step 4: Run GREEN**

Run `LearningEvidenceGraphServiceTest`.

### Task 3: Route B TutorBench Trajectory Evaluation

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/learner/effect/LearningEffectSummaryDto.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/effect/LearningEffectTraceRepository.java`
- Modify: `backend/src/main/java/com/pythontopia/tutorbench/TutorBenchEvaluator.java`
- Modify: `backend/src/main/java/com/pythontopia/tutorbench/TutorBenchService.java`
- Test: `backend/src/test/java/com/pythontopia/tutorbench/TutorBenchEvaluatorTest.java`
- Test: `backend/src/test/java/com/pythontopia/learner/effect/LearningEffectTraceServiceTest.java`

**Step 1: Write failing tests**

Add tests for:

- `evidence_graph_connectivity`
- `grounded_personalization`
- `trajectory_traceability`
- quantitative comparison: graph-backed summary pass rate beats naive summary.

**Step 2: Run RED**

Expected: new TutorBench cases absent.

**Step 3: Implement minimal graph metrics propagation**

Add evidence graph metrics fields to `LearningEffectSummaryDto` with a backward-compatible constructor. `LearningEffectTraceRepository` may compute graph metrics directly or call a service from `LearningEffectTraceService`.

**Step 4: Run GREEN**

Run:

```bash
cd backend
mvn -s ~/.m2/settings.xml -q -Dtest=TutorBenchEvaluatorTest,LearningEffectTraceServiceTest,LearningEvidenceGraphServiceTest test
```

### Task 4: Route C Memory-Aware Personalization Signals

**Files:**
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/EvidencePersonalizationSignalDto.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/evidence/LearningEvidenceGraphService.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/recommendation/LearnerRecommendationService.java`
- Test: `backend/src/test/java/com/pythontopia/learner/recommendation/LearnerRecommendationServiceTest.java` or create focused evidence test if existing test setup is too broad.

**Step 1: Write failing personalization test**

Seed repeated failed checks and page-grounded resources. Assert signals include:

- repeated weak KC
- remediation pressure
- evidence event ids or check ids
- page ids

**Step 2: Run RED**

Expected: no personalization signal API.

**Step 3: Implement signals**

Expose signals from `LearningEvidenceGraphService`. Keep recommendation integration conservative: use signals to enrich reasons and evidence ids, not to replace mastery-based ranking wholesale.

**Step 4: Run GREEN**

Run focused backend tests.

### Task 5: Quantitative Comparison Harness

**Files:**
- Create: `backend/src/test/java/com/pythontopia/learner/evidence/LearningEvidenceGraphQuantComparisonTest.java`

**Step 1: Write failing test**

Build two scenarios:

- naive: resource exists but no check/page/mastery evidence
- graph-backed: resource + check + mastery + page + KC relation

Assert:

- graph-backed `graphCompletenessScore` > naive by at least 0.4
- graph-backed TutorBench pass rate > naive pass rate
- graph-backed connected evidence rate > naive connected evidence rate

**Step 2: Run RED**

Expected: harness cannot compile until metrics/service are complete.

**Step 3: Implement missing helpers**

Add only deterministic metric helpers needed for the comparison.

**Step 4: Run GREEN**

Run quant comparison test.

### Task 6: Docs, CHANGELOG, Verification

**Files:**
- Modify: `CHANGELOG.md`
- Read: `docs/plans/2026-06-24-learning-evidence-graph-design.md`
- Read: `docs/plans/2026-06-24-learning-evidence-graph-implementation.md`

**Step 1: Update CHANGELOG**

Add a top entry with:

- Route A/B/C implementation summary
- advanced AI concepts mapped to concrete repo features
- verification commands and numeric comparison results
- unverified limits, especially real long-term A/B and full browser click-through if not run

**Step 2: Run final verification**

Run:

```bash
cd backend
mvn -s ~/.m2/settings.xml -q -Dtest=LearningEvidenceGraphServiceTest,LearningEvidenceGraphQuantComparisonTest,LearningEffectTraceServiceTest,TutorBenchEvaluatorTest test
cd frontend
npm run typecheck
```

Add frontend tests only if UI is touched.

