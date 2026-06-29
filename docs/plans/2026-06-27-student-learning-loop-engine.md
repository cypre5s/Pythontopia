# Student Learning Loop Engine Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make resource consumption close the real learning loop: read evidence, micro-check or operation evidence, practice submission signals, mastery deltas, and a structured next action.

**Architecture:** Extend the existing workshop resource flow instead of creating a parallel demo. `ResourceWorkshopService` becomes the decision point that turns evidence into `PASS`, `REMEDIATE`, `SWAP_RESOURCE`, or `REPLAN`; `MemoryBriefService` upgrades from event snippets to a compact learner-memory profile that can be injected into resource generation and guide/workshop flows. Frontend renders this as one compact action rail in `ResourceDetailPanel`, avoiding extra explanatory cards.

**Tech Stack:** Spring Boot Java 21, JDBC repositories, existing `LearningEvent`/`MasteryEvaluator`/memory services, Vue 3 + TypeScript + Pinia, Vitest, Playwright.

---

### Task 1: Backend Loop Contract

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/workshop/dto/ResourceConsumptionResponse.java`
- Modify: `backend/src/main/java/com/pythontopia/workshop/dto/ResourceCheckResponse.java`
- Modify: `backend/src/main/java/com/pythontopia/workshop/service/ResourceWorkshopService.java`
- Test: `backend/src/test/java/com/pythontopia/workshop/ResourceWorkshopServiceTest.java`

Steps: write failing tests for pass/remediate/swap/replan decisions, implement a small deterministic policy, verify service tests.

### Task 2: Deep Memory Brief

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/learner/memory/dto/MemoryBriefDto.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/memory/MemoryBriefService.java`
- Modify: `backend/src/test/java/com/pythontopia/learner/memory/MemoryBriefServiceTest.java`
- Modify graph contract if the backend JSON shape needs mirrored fields.

Steps: write failing tests for stable preferences, struggle patterns, recent wins, and recommended intervention; implement aggregation from recalled memories, review state, and profile dimensions.

### Task 3: Compact Frontend Action Rail

**Files:**
- Modify: `frontend/src/api/dto/Workshop.ts`
- Modify: `frontend/src/components/workshop/ResourceDetailPanel.vue`
- Test: `frontend/tests/components/workshop/ResourceDetailPanel.spec.ts`

Steps: write failing tests for compact loop state and next-action rendering, implement minimal action rail, keep text short and avoid adding new card stacks.

### Task 4: Multi-Round E2E

**Files:**
- Add/modify: `frontend/e2e/*workshop*`
- Verify: backend focused tests, frontend focused tests, full-stack browser flow with at least pass and remediation rounds.

Steps: start the real stack with `./dev.sh`, run focused Playwright flows, fix any revealed bug, rerun.
