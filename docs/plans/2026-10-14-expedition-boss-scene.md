# Expedition Boss Scene Implementation Plan

> **Status:** Implemented and locally verified on 2026-10-14. See the execution record below.

**Goal:** Add a responsive Phaser boss battle scene to Web Endless Expedition, driven only by settled judge results and real run HP.

**Architecture:** Keep the existing single `Phaser.Game`; switch between the existing tower scene and a plain-object Boss scene within that game. Keep challenge/editor/result text in Vue DOM, and reuse the project's audited boss portraits, `useAudio`, and Kenney CC0 impact audio.

**Tech Stack:** Vue 3, strict TypeScript, Phaser 3.87, Vitest, existing `scripts/sync-assets.mjs` asset manifest.

---

### Task 1: Render the Boss arena in a dedicated Phaser scene

**Files:**
- Create: `frontend/src/game/scenes/ExpeditionBossScene.ts`
- Test: `frontend/tests/game/scenes/ExpeditionBossScene.spec.ts`

**Step 1: Add a failing scene test**

Add a boss view fixture and assert that it preloads only the selected Boss portrait, renders the Boss name, real phase count, and exact HP. Assert that a settled pass triggers one hit effect and a repeated identical view does not trigger it again; a failed result does not trigger Boss damage.

**Step 2: Run the focused test**

Run: `cd frontend && npx vitest run tests/game/scenes/ExpeditionBossScene.spec.ts`
Expected: FAIL because the Boss scene module does not exist yet.

**Step 3: Implement the minimal scene branch**

Add a Boss scene DTO/bridge and manager helpers that start the plain-object scene in the existing game. Draw the arena and the selected existing chapter portrait, and animate only on a unique settled pass. Use camera shake/particles only after checking the installed Phaser 3.87 types. Leave judging/settlement and all learning content in Vue.

**Step 4: Run the focused test**

Run: `cd frontend && npx vitest run tests/game/scenes/ExpeditionBossScene.spec.ts`
Expected: PASS.

### Task 2: Preserve the canvas across map-to-Boss navigation

**Files:**
- Modify: `frontend/src/components/expedition/ExpeditionCanvas.vue`
- Modify: `frontend/src/game/scenes/ExpeditionScene.ts`
- Modify: `frontend/src/pages/EndlessExpeditionPage.vue`
- Test: `frontend/tests/pages/EndlessExpeditionPage.spec.ts` (create only if no equivalent page integration test exists)

**Step 1: Add a failing page-level test**

Verify that entering a Boss node keeps one `ExpeditionCanvas` instance visible, supplies the current phase/HP/result DTO, and keeps the `ChallengeWorkspace` and its submit action available in DOM. Verify a non-Boss challenge does not show the Boss scene.

**Step 2: Run the focused page test**

Run: `cd frontend && npx vitest run tests/pages/EndlessExpeditionPage.spec.ts`
Expected: FAIL until the page branches share the canvas lifecycle and pass Boss state.

**Step 3: Implement the shared active-run layout**

Keep map and Boss challenge in one stable parent branch, with one canvas between map controls and the challenge/editor. Switch its view in-place for Boss nodes. Feed the result only after loading and authoritative settlement are finished. Keep screen-reader status and all challenge controls in DOM.

**Step 4: Run the focused page test**

Run: `cd frontend && npx vitest run tests/pages/EndlessExpeditionPage.spec.ts`
Expected: PASS.

### Task 3: Register and use one CC0 impact sound

**Files:**
- Copy: `assets/inbox/sfx_jump/kenney_impact-sounds/impactSoft_medium_000.ogg` to `frontend/public/assets/pixel/sfx/boss/impactSoft_medium_000.ogg`
- Modify: `scripts/sync-assets.mjs` only if the existing manifest should own the new runtime path; otherwise register the reviewed copied asset explicitly.
- Modify: `frontend/src/game/scenes/BootScene.ts`
- Modify: `frontend/src/composables/useAudio.ts`
- Modify: `LICENSE-ART.md`
- Modify: `frontend/public/assets/pixel/LICENSE.md`

**Step 1: Add a failing audio registration assertion**

Extend the existing BootScene/audio test to assert the new key and runtime path are registered. If no focused test exists, add a small test in the nearest existing BootScene suite.

**Step 2: Run the focused test**

Run: `cd frontend && npx vitest run tests/game/scenes/BootScene.spec.ts`
Expected: FAIL because the Boss impact key is absent.

**Step 3: Add the runtime asset entry**

Copy only the selected OGG into the runtime Boss folder, add it to `SFX_LIST` and `SfxKey`, and play it via `useAudio` only after a settled successful answer. Update both required license indexes with Kenney, CC0, source URL, and runtime purpose.

**Step 4: Run the focused test**

Run: `cd frontend && npx vitest run tests/game/scenes/BootScene.spec.ts`
Expected: PASS.

### Task 4: Verify the integrated change

**Files:**
- Review: the Task 1-3 diff and tests.

**Step 1: Run focused frontend tests**

Run: `cd frontend && npx vitest run tests/game/scenes/ExpeditionScene.spec.ts tests/game/scenes/BootScene.spec.ts tests/pages/EndlessExpeditionPage.spec.ts`
Expected: all selected tests pass.

**Step 2: Run static checks and production build**

Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: exit code 0 for each command.

**Step 3: Check scope and asset registration**

Run: `git diff --check` and `node scripts/sync-assets.mjs --check`.
Expected: no whitespace errors; asset sync check reports no missing required files.

## Execution record

- Added `ExpeditionBossScene` and routed the existing expedition canvas between map and Boss scenes without creating another `Phaser.Game`. The editor, submission controls, result, HP, and live announcement remain in Vue DOM.
- The scene only runs its hit effect/audio after a settled passing result. The failed-result path keeps Boss health intact and displays the run's real HP. Existing portraits are reused; one Kenney CC0 impact sound is registered in both license indexes and the asset sync manifest.
- Focused frontend verification passed: 4 files / 17 tests, `npm run typecheck`, targeted ESLint, production build, `git diff --check`, and `node scripts/sync-assets.mjs --check`.
- Local Chromium full-stack smoke passed: one canvas survived map-to-Boss transition, the portrait and OGG returned HTTP 200, and there were no page errors or failed requests. The pass/fail visual-state browser probe used injected settled state; actual judging is covered by backend/page tests.
- Follow-up verification: a Phaser scene unit test confirms only a settled pass animates a single hit for an attempt id and a failed result never damages the Boss. Two Chromium guide flows passed for the expedition map and a reachable course-problem node. The complete final Boss multi-phase real-submit browser journey remains unverified.
- Final interaction pass adds a brief player lunge and a pixel impact burst to the existing one-time settled-pass effect. The Phaser scene test now checks the lunge and effect count; four related Web test files (14 tests), typecheck, production build, and changed-file ESLint pass. No new asset or dependency was added.
- A later real Web→Spring→Graph→DeepSeek coverage check generated a reviewed AI problem for no-static-problem KC `PY43-KC-012`; an answer derived only from its public statement/sample passed the formal Boss judge 3/3. The public endpoint exposed no hidden cases or reference answer, the browser emitted no errors, and the temporary student/review data were removed. This verifies generated-question judging, not a complete Phaser Boss victory journey.
- Performance sampling and limitations are recorded in `docs/plans/2026-10-13-digital-twin-simplification-worklog.md`.
