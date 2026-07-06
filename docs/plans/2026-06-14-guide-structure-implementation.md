# Guide Structure Improvement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Improve `/guide` with grouped global navigation, reliable sticky navigation, a non-duplicated page outline, and richer AI Guide and Baixiao Workshop explanations.

**Architecture:** Preserve the existing `?topic=<id>` routing and structured content source. Add navigation grouping metadata in `guideSections.ts`, render it through `GuideSidebar.vue`, and keep layout behavior in `GuidePage.vue`; content enhancements stay data-driven in `guideSections.ts`.

**Tech Stack:** Vue 3, TypeScript strict, Vue Router, Vitest, scoped CSS, Playwright for browser acceptance.

---

### Task 1: Lock Navigation Responsibilities With Tests

**Files:**
- Modify: `frontend/tests/pages/GuidePage.spec.ts`

1. Add a failing test for the three global directory groups and their complete topic coverage.
2. Add a failing test proving the right outline contains only current-page anchors and no "其他主题" section.
3. Add a failing test proving the active mobile directory includes current-page subsection links.
4. Run `npm run test -- --run tests/pages/GuidePage.spec.ts` and confirm the new assertions fail for missing behavior.

### Task 2: Implement Grouped and Sticky Navigation

**Files:**
- Modify: `frontend/src/content/guideSections.ts`
- Modify: `frontend/src/components/guide/GuideSidebar.vue`
- Modify: `frontend/src/pages/GuidePage.vue`

1. Define typed navigation groups that reference existing topic IDs.
2. Render group labels and active-topic subsection links in the left directory.
3. Remove repeated global topic links from the right outline.
4. Make desktop side columns sticky below the application header with bounded internal scrolling.
5. Add an inline current-page outline for medium layouts and preserve the collapsible mobile navigation.
6. Run the focused GuidePage test until it passes.

### Task 3: Expand AI Guide and Workshop Explanations

**Files:**
- Modify: `frontend/tests/pages/GuidePage.spec.ts`
- Modify: `frontend/src/content/guideSections.ts`

1. Add failing content assertions for AI Guide context sources, staged problem guidance, limitations, and Workshop handoff.
2. Add failing content assertions for Workshop inputs, 16-capability routing, multi-agent review, outputs, and artifact reuse.
3. Restructure and expand the two topic sections without changing backend contracts or inventing unavailable features.
4. Run the focused test until it passes.

### Task 4: Refine the Reading Surface

**Files:**
- Modify: `frontend/src/pages/GuidePage.vue`
- Modify: `frontend/src/components/guide/GuideSidebar.vue`
- Modify: `frontend/src/components/guide/GuideSection.vue`

1. Compact the hero and add a visible learning-loop orientation strip.
2. Improve article spacing and navigation active states while preserving the existing warm pixel-document visual language.
3. Verify reduced-motion and keyboard focus behavior remain intact.

### Task 5: Verify and Deploy

**Files:**
- Reuse: `deploy/scripts/build_release_archive.sh`
- Reuse: `deploy/scripts/install_release.sh`
- Reuse: `deploy/scripts/public_acceptance.sh`

1. Run the focused GuidePage test, frontend type check, and production build.
2. Start or reuse the local stack and inspect `/guide` at desktop and mobile widths.
3. Build and verify the self-contained release archive.
4. Confirm the direct route to `47.120.29.62` and add the documented host route only if WSL TUN interception is active.
5. Upload and install the release on `ecs-pythontopia` using the existing release scripts.
6. Verify `http://47.120.29.62/guide`, direct topic navigation, screenshots, and browser console output.
