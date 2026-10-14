# AI Question Bank Auto Review Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a Graph-reviewed AI Boss question to the shared bank when it passes strict server-side eligibility checks, while preserving review provenance and teacher override.

**Architecture:** Reuse `boss_problem_review` and its approved-bank lookup. Add a `review_source` audit value, atomically deduplicate automatic approvals, and route both real-time and prefetched AI candidates through the same repository method. Teacher/admin review pages can confirm or reject automatic approvals; existing manual terminal decisions remain immutable.

**Tech Stack:** Java 21, Spring JDBC, PostgreSQL/Flyway, Vue 3, TypeScript, Vitest.

---

### Task 1: Persist automatic approval provenance

**Files:**
- Create: `backend/src/main/resources/db/migration/V213__boss_problem_review_automated_source.sql`
- Modify: `backend/src/main/java/com/pythontopia/world/boss/review/BossProblemReviewRecord.java`
- Modify: `backend/src/main/java/com/pythontopia/world/boss/review/BossProblemReviewRepository.java`
- Modify: `backend/src/main/java/com/pythontopia/world/boss/review/BossProblemReviewService.java`
- Test: `backend/src/test/java/com/pythontopia/world/boss/review/BossProblemReviewRepositoryPostgresTest.java`
- Test: `backend/src/test/java/com/pythontopia/world/boss/review/BossProblemReviewSchemaTest.java`

**Steps:** Add failing real-PostgreSQL tests for eligible auto-approval, invalid candidate rejection, duplicate suppression, provenance, and teacher override. Run them to confirm failure. Add the forward-only migration and the smallest repository/record/service changes. Run both repository and schema tests.

### Task 2: Route both AI generation callers through the shared bank gate

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/world/boss/BossService.java`
- Modify: `backend/src/main/java/com/pythontopia/world/boss/EndlessPregenService.java`
- Test: `backend/src/test/java/com/pythontopia/world/boss/BossServiceTest.java`
- Test: `backend/src/test/java/com/pythontopia/world/boss/EndlessPregenServiceTest.java`

**Steps:** Add failing tests that verify both callers submit exact chapter/stage/KC/difficulty context to the auto-approval repository method. Run the focused tests. Replace the old pending-queue calls and run both service test classes.

### Task 3: Show provenance and allow teacher review of automatic approvals

**Files:**
- Modify: `frontend/src/api/problemReview.ts`
- Modify: `frontend/src/pages/admin/AdminProblemReviewsPage.vue`
- Modify: `frontend/src/pages/teacher/TeacherReviewPage.vue`
- Test: `frontend/src/pages/admin/__tests__/AdminProblemReviewsPage.spec.ts`
- Test: `frontend/tests/pages/TeacherReviewPage.spec.ts`
- Test: `frontend/tests/api/problemReview.spec.ts`

**Steps:** Add failing tests for the automatic source label and teacher rejection action, then implement only those visible controls and run their Vitest suites plus typecheck/lint.

### Task 4: Verify real use and record evidence

**Files:**
- Modify: `docs/project/CHANGELOG.md`
- Modify: `docs/operations/execution_log.md`
- Modify: `docs/plans/2026-10-14-web-engineering-cleanup-worklog.md`

**Steps:** Apply Flyway locally, exercise one real DeepSeek-generated question through the Web Boss API, verify its `AUTOMATED/APPROVED` row and mastery eligibility, request the same KC again to prove approved-bank reuse, then clean temporary test data. Record only fresh results and remaining limits.
