# Admin Management and World Controls Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete the approved administrator management modules and restore reliable player movement in the World scene before validating and deploying the release.

**Architecture:** Reuse the existing Spring/JDBC domain APIs and admin layout, adding only admin-scoped query/config endpoints and persistence needed for resource state and usage history. Keep game input in the Phaser adapter/WorldScene boundary and reuse the current tile-step movement, collision, and persistence path for keyboard and click routing.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL/Flyway, Vue 3 + TypeScript, Phaser 3, Vitest, Maven, FastAPI Graph, Playwright/Chromium, Docker Compose release archive.

---

## Task 1: Reproduce and repair World movement

**Files:**
- Modify: `frontend/src/game/scenes/WorldScene.ts`
- Modify: `frontend/src/game/adapters/sceneTypes.ts` only if the scene test/input boundary needs a typed event contract
- Create: `frontend/src/game/core/WorldPathfinding.ts`
- Create: `frontend/src/game/core/__tests__/WorldPathfinding.spec.ts`
- Add the smallest focused World input regression under the existing frontend test layout

**Steps:**
1. Start `./dev.sh`; with a local student, open `/world`, try W/A/S/D and record position changes, focus, active Phaser scenes, console and failed requests.
2. Verify the click baseline on an empty walkable tile. Preserve the current NPC/building click interactions.
3. Write failing pure tests for a reachable route around walls, unreachable targets, blocked start/target, and fixed cardinal movement.
4. Implement bounded four-neighbor BFS over the current map dimensions and collision grid.
5. Fix the reproduced keyboard focus/input cause at the shared input boundary; do not bypass the existing movement/collision/store path.
6. Add ground-click destination tracking in `WorldScene`; keyboard input cancels the click route, a new click replaces it, blocked targets do not move, and shutdown clears listeners/path state.
7. Run the focused Vitest suite and `npm run typecheck`; then verify keyboard, click routing, collision and page lifecycle in Chromium.

## Task 2: Student details and administrator classroom management

**Files:**
- Add admin DTO/repository/service/controller under `backend/src/main/java/com/pythontopia/admin/`
- Reuse `ProfileRepository`, mastery repositories, `ClassroomRepository`, and `ClassroomDashboardRepository` where their contracts fit
- Add backend tests under `backend/src/test/java/com/pythontopia/admin/`
- Add typed frontend clients under `frontend/src/api/`
- Add `AdminStudentDetailPage.vue` and `AdminClassroomsPage.vue` with focused tests under `frontend/src/pages/admin/`
- Modify `AdminUsersPage.vue`, `AdminLayout.vue`, and `frontend/src/app/router.ts`

**Steps:**
1. Add failing tests for admin-only cross-user reads, student detail composition, empty profile/progress, class list and class detail authorization.
2. Add bounded, paginated admin read endpoints; return the existing eight profile dimensions, mastery, activity, submissions/judging and class membership without changing learner self APIs.
3. Add all-class summaries and reuse the existing class dashboard queries for detail; keep destructive class deletion out of scope.
4. Add account-row navigation and class/member screens using real API DTOs, with KC labels resolved to Chinese names.
5. Run the targeted Maven and Vitest suites plus frontend typecheck.

## Task 3: Course resource administration

**Files:**
- Add migration after current `V230` in `backend/src/main/resources/db/migration/`
- Add admin resource DTO/repository/service/controller under `backend/src/main/java/com/pythontopia/admin/`
- Update the course-pack read path only where status enforcement is required
- Add migration/repository/controller integration tests under `backend/src/test/java/com/pythontopia/admin/`
- Add `frontend/src/api/adminCourseResources.ts`, `AdminCourseResourcesPage.vue`, and page tests
- Modify admin router/layout and record the migration in `docs/operations/execution_log.md`

**Steps:**
1. Assert the seeded course pack remains exactly 7 documents, 561 pages, 7 chapters, 65 KCs, 46 examples and 39 problems.
2. Add failing admin tests for catalog, preview, status updates, invalid identifiers and non-admin denial.
3. Add a reversible resource-state overlay keyed to seeded resource identity; do not mutate/delete course seed files or rows.
4. Apply disabled state consistently to public catalog/stream reads and any resource retrieval path it governs; preserve RAG citations and distinguish unavailable/unknown resources.
5. Build the searchable preview/status page using the existing admin components and DTOs.
6. Run targeted Maven/Vitest tests and the course runtime seed validator.

## Task 4: AI question administration

**Files:**
- Reuse `BossProblemReviewController`, `BossProblemReviewService`, and `BossProblemReviewRepository`
- Extend `frontend/src/api/problemReview.ts` and `AdminProblemReviewsPage.vue`
- Extend backend authorization/service tests and `AdminProblemReviewsPage.spec.ts`

**Steps:**
1. Add failing tests proving ADMIN can generate/review, students cannot, and approval/rejection retains audit reasons.
2. Add a typed administrator generation form backed by the existing generation endpoint and course chapter metadata.
3. Reuse the existing status/search queue for candidate history; keep hidden tests and answer keys out of student responses.
4. Run targeted Maven/Vitest tests and verify formal student submission still invokes the current Judge service.

## Task 5: Consumption history and AI provider configuration

**Files:**
- Add migrations after `V230` for encrypted provider settings and only the missing per-call usage audit fields
- Add admin config/usage DTOs, repositories, services and controllers under `backend/src/main/java/com/pythontopia/admin/`
- Add the encryption key property to backend configuration and production/development templates without storing a real secret
- Update Graph runtime provider configuration only through its existing internal-token boundary
- Add backend and Graph provider/config tests
- Add typed API modules and `AdminAiConfigPage.vue` / `AdminUsagePage.vue` with focused Vitest tests
- Modify `AdminLayout.vue`, router and `deploy/release/README.md`

**Steps:**
1. Add failing tests for masked/preserved API keys, invalid provider/model rejection, encryption round-trip, missing master key, live apply failure, ADMIN-only access, and unknown historical usage values.
2. Store only authenticated ciphertext at rest; return a mask and preserve the saved key when an update omits it.
3. Validate the existing production DeepSeek base/model allowlist and add explicit provider connection testing without logging credentials.
4. Apply provider updates live over the authenticated internal service channel; startup continues to load the existing environment config when no database override exists.
5. Consolidate actual historical request/run/resource/media/judge data; persist future provider-reported token counts, never invent past tokens or monetary costs.
6. Build filterable usage and configuration pages; show unavailable historical metrics as unknown.
7. Run targeted backend/Graph/Vitest tests and validate production/development Compose configuration with placeholder values only.

## Task 6: KC display audit and changelog

**Files:**
- Modify `frontend/src/data/kcNames.ts` and only the remaining user-facing renderers found by the source audit
- Extend KC name/humanization tests and affected component tests
- Update `docs/operations/execution_log.md` with current verified results

**Steps:**
1. Add failing tests for known and unknown raw KC identifiers in visible text.
2. Change unknown-code display fallback so raw identifiers are never echoed; retain internal route/API identifiers.
3. Audit rendered learner, teacher and admin strings including AI/NPC text and fix only visible leak paths.
4. Run the KC source scan, relevant Vitest suites, typecheck and lint.

## Task 7: Full local verification and browser acceptance

1. Run all changed backend tests, full frontend tests/typecheck/lint/build, Graph tests, RAG tests, and course seed validation required by `AGENTS.md`.
2. Start the real local stack and use Chromium to exercise admin permissions and data views plus World WASD/click movement; record console, page errors and failed requests.
3. Verify question submission reaches formal Judge and course/AI configs do not expose secrets or KC identifiers.
4. Update `docs/operations/execution_log.md`; do not claim unverified ECS or browser behavior.

## Task 8: ECS deployment, acceptance, and final GitHub push

1. Confirm ECS identity/current release/free disk and route to `47.120.29.62`; do not delete the PostgreSQL volume or user/course data.
2. Build and verify the offline release archive locally, including images, checksums, course assets and migrations.
3. Upload the archive and install through `deploy/scripts/install_release.sh`, which backs up the database/config and retains rollback release.
4. Verify all seven services, public HTTPS, database migration, admin authorization, actual browser game movement and no browser/API errors.
5. If ECS verification passes, stage the verified implementation/log and push `main` to `origin`; if deployment fails, use the release rollback procedure and do not push an unverified release.
