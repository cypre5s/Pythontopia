# Mainline Map Input Isolation — 2026-10-15

## Finding

The first Twin action correctly opened `/mainline-map`, but clicking a chapter drawer button also reached Phaser's window-level `mouseup` listener. The scene treated that UI click as a map-node click, switching the drawer to a locked chapter and preventing the stage route from opening. The drawer already stopped pointer events, while Phaser also listens for mouse events.

## Change

- Stop `mousedown` and `mouseup` at the existing map drawer, alongside its pointer-event stops.
- Extend the page regression to cover the actual mouse events Phaser listens for.

## Verification

- RED: `npm run test -- tests/pages/MainlineMapPage.spec.ts` failed because the window `mousedown` listener received the drawer event.
- GREEN: the focused page suite passed, 13/13; `npm run typecheck` and ESLint for the page/spec passed.
- Real browser with a disposable student: Twin → `/mainline-map` retained selected chapter 1 → “继续学习” opened `/mainline/stage/1`; stage detail API returned HTTP 200 with 3 videos, 15 courseware pages, and 4 questions; browser errors 0. The account was logged out and deleted.

## Still open

This confirms the route into the first lesson. Completing the three first-chapter videos, courseware, practice, and assessment to reach chapter 2's first coding KC remains pending; the server requires 440 seconds of actual watched time per first-chapter stage.
