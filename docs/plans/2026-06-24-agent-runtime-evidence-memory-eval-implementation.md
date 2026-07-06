# Agent Runtime, Evidence Memory, Eval Optimization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the three-route deepening: protocolized Agent Runtime, Evidence Memory 2.0, and Eval/Prompt Optimization with tests and quantitative comparisons.

**Architecture:** Keep existing bounded AI workflows. Add canonical runtime quality metrics and protocol descriptors, derive evidence memory snapshots from the SQL-native evidence graph, and add an offline optimizer around the existing eval harness.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, PostgreSQL JSONB, Flyway, JUnit 5, FastAPI/Pydantic, LangGraph, Vitest, Vue 3, AG-UI-style SSE, MCP-style descriptors, OpenTelemetry GenAI semantic attributes.

---

### Task 1: Runtime Quality Metrics And AG-UI Orchestrator Stream

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/sse_agui.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/routes/orchestrator.py`
- Test: `services/pythontopia-graph/tests/test_sse_agui.py`
- Test: `services/pythontopia-graph/tests/test_orchestrator_route.py`

**Steps:**

1. Write failing tests for canonical AG-UI stream quality.
2. Run targeted pytest and confirm failure.
3. Add `stream_quality(events)` to `sse_agui.py`.
4. Change orchestrator streaming `_format_sse` to `sse_agui.translate`.
5. Emit `RUN_STARTED` and keep existing `STATE_DELTA` progress.
6. Rerun targeted pytest.

### Task 2: Protocol Descriptor Deepening

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/protocol/ProtocolGatewayDescriptorDto.java`
- Create: `backend/src/main/java/com/pythontopia/protocol/ProtocolResourceDto.java`
- Create: `backend/src/main/java/com/pythontopia/protocol/ProtocolPromptDto.java`
- Modify: `backend/src/main/java/com/pythontopia/protocol/ProtocolGatewayService.java`
- Test: `backend/src/test/java/com/pythontopia/protocol/ProtocolGatewayServiceTest.java`

**Steps:**

1. Write failing tests for MCP resources/prompts and OTel GenAI attributes.
2. Run targeted Maven test and confirm failure.
3. Extend descriptor DTOs.
4. Populate real tools/resources/prompts/events/attributes.
5. Rerun targeted Maven test.

### Task 3: Evidence Memory Snapshot

**Files:**
- Create: `backend/src/main/resources/db/migration/V180__learner_evidence_memory_snapshot.sql`
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/EvidenceMemorySignalDto.java`
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/EvidenceMemorySnapshotDto.java`
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/EvidenceMemoryRepository.java`
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/EvidenceMemoryService.java`
- Create: `backend/src/main/java/com/pythontopia/learner/evidence/EvidenceMemoryController.java`
- Test: `backend/src/test/java/com/pythontopia/learner/evidence/EvidenceMemoryServiceTest.java`

**Steps:**

1. Write failing Testcontainers tests for persisted snapshots and relation-expanded signals.
2. Run targeted Maven test and confirm failure.
3. Add migration and DTOs.
4. Implement snapshot derivation from Learning Evidence Graph plus `course_kc_edge` propagation.
5. Persist snapshot JSONB and expose a read endpoint.
6. Rerun targeted Maven test.

### Task 4: Evidence Memory Recommendation Integration

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/learner/recommendation/LearnerRecommendationService.java`
- Modify: `backend/src/test/java/com/pythontopia/learner/recommendation/RecommendationServiceTest.java`

**Steps:**

1. Write failing test where evidence-memory weak KC outranks a mastery-only weaker KC.
2. Run targeted Maven test and confirm failure.
3. Inject `EvidenceMemoryService`.
4. Rerank after explicit insight and before raw memory fallback.
5. Include evidence event ids and check ids in the reason/evidence list.
6. Rerun targeted Maven test.

### Task 5: Eval Optimization Factory

**Files:**
- Create: `services/pythontopia-graph/pythontopia_graph/eval/optimization.py`
- Test: `services/pythontopia-graph/tests/test_eval_optimization.py`

**Steps:**

1. Write failing tests for baseline-vs-candidate uplift, bounded regressions, and rejected weak candidates.
2. Run targeted pytest and confirm failure.
3. Implement deterministic optimizer models and scoring.
4. Rerun targeted pytest.

### Task 6: Quantitative Comparison And Changelog

**Files:**
- Modify: `CHANGELOG.md`
- Test: route-specific tests plus full impacted suites.

**Steps:**

1. Add quantitative comparison assertions:
   - canonical AG-UI quality > legacy quality
   - evidence-memory graph score > naive memory score
   - optimizer best candidate > baseline by configured uplift
2. Run backend targeted tests.
3. Run graph targeted tests.
4. Run frontend AG-UI/useSseStream tests.
5. Run broader impacted suites.
6. Update `CHANGELOG.md` with implementation, tests, metrics, and remaining risk.
