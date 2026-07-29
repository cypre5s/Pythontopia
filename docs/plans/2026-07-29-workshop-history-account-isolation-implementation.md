# Workshop History Account Isolation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prevent workshop chat history and cached workshop UI state from crossing account boundaries in one browser session.

**Architecture:** Keep the existing history API as a small query repository, but make its cache owner explicit with `userId + limit` keys and cancellable reads. Propagate the authenticated user id through WorkshopPage and ChatHistoryRail, clear the repository on session transitions, and rebuild the KeepAlive cache when the account changes.

**Tech Stack:** Vue 3.5, TypeScript strict, Pinia, Axios, Vitest, Vue Test Utils, Playwright.

---

### Task 1: Make the history query repository owner-aware and cancellable

**Files:**
- Modify: `frontend/tests/api/workshopHistory.spec.ts`
- Modify: `frontend/src/api/workshopHistory.ts`

**Step 1: Write the failing owner-isolation test**

Change history reads and cache writes to the desired explicit API:

```ts
await listWorkshopChatHistoryTurns(101, 50, { now: () => 1000 });
await listWorkshopChatHistoryTurns(202, 50, { now: () => 2000 });
rememberWorkshopChatHistoryTurn(101, turn, 50, { now: () => 1500 });
```

Assert two users with the same `limit` issue separate GET requests and receive only their own cached turns.

**Step 2: Write the failing cancellation test**

Capture the Axios `signal` passed to `http.get`, leave the promise pending, call:

```ts
clearWorkshopChatHistoryCache();
```

Assert the signal is aborted.

**Step 3: Run RED**

Run:

```bash
cd frontend
npx vitest run tests/api/workshopHistory.spec.ts
```

Expected: compile failures because the owner-aware signatures and public clear function do not exist.

**Step 4: Implement the minimal repository change**

- Add positive-integer `WorkshopHistoryOwnerId`.
- Key cache entries by `${userId}:${limit}`.
- Track active `AbortController` instances.
- Capture a repository generation before each request and only cache results if it is still current.
- Pass `signal` only as Axios transport configuration; never send `userId` as an authorization parameter.
- In `finally`, remove the completed controller from the active set.
- Export `clearWorkshopChatHistoryCache()` to advance the generation, abort all active reads, and clear all cache entries.
- Keep `resetWorkshopChatHistoryCacheForTest()` as a compatibility alias that calls the production clear function.

**Step 5: Run GREEN**

Run:

```bash
cd frontend
npx vitest run tests/api/workshopHistory.spec.ts
```

Expected: all workshop history API tests pass.

**Step 6: Record evidence**

Append the RED and GREEN commands/results to
`docs/plans/2026-07-29-incremental-refactoring-worklog.md`.

---

### Task 2: Bind query cache cleanup to session transitions

**Files:**
- Modify: `frontend/tests/stores/session.spec.ts`
- Modify: `frontend/src/stores/session.ts`

**Step 1: Write failing session cleanup tests**

Mock `clearWorkshopChatHistoryCache` and assert:

- successful login clears history cache once before other account state can be reused;
- logout clears history cache once;
- failed login does not clear the active account cache.

**Step 2: Run RED**

Run:

```bash
cd frontend
npx vitest run tests/stores/session.spec.ts
```

Expected: cleanup assertions fail because session actions do not call the repository.

**Step 3: Implement the minimal lifecycle hook**

Import `clearWorkshopChatHistoryCache` in the session store. Call it after a successful
login response is obtained and before account-scoped stores are reused; call it during logout
before clearing the session snapshot. Do not clear on refresh for the same authenticated user.

**Step 4: Run GREEN**

Run:

```bash
cd frontend
npx vitest run tests/stores/session.spec.ts
```

Expected: all session tests pass.

**Step 5: Record evidence**

Append RED/GREEN results to the worklog.

---

### Task 3: Prevent stale component and KeepAlive state after an account change

**Files:**
- Modify: `frontend/tests/components/workshop/ChatHistoryRail.spec.ts`
- Modify: `frontend/src/components/workshop/ChatHistoryRail.vue`
- Modify: `frontend/tests/pages/WorkshopPage.spec.ts`
- Modify: `frontend/src/pages/WorkshopPage.vue`
- Modify: `frontend/src/test/App.test.ts`
- Modify: `frontend/src/App.vue`

**Step 1: Write the failing history-rail ownership tests**

- Mount the rail with `userId=101` and load A's turn.
- Change the prop to `userId=202`.
- Assert A's text disappears immediately and a second owner-aware read begins.
- Resolve A's old deferred read after the change and assert it cannot overwrite B's list.

**Step 2: Write the failing WorkshopPage ownership tests**

Seed an authenticated Pinia session with user id 101. Assert:

```ts
expect(listWorkshopChatHistoryTurns).toHaveBeenCalledWith(101, 50, expect.anything());
expect(rememberWorkshopChatHistoryTurn).toHaveBeenCalledWith(101, savedTurn);
```

**Step 3: Write the failing KeepAlive reset test**

Use a counted WorkshopPage test double in `src/test/App.test.ts`. Navigate as user 101,
change the session to user 202, and assert the cached WorkshopPage instance is recreated.

**Step 4: Run RED**

Run:

```bash
cd frontend
npx vitest run \
  tests/components/workshop/ChatHistoryRail.spec.ts \
  tests/pages/WorkshopPage.spec.ts \
  src/test/App.test.ts
```

Expected: owner-aware calls, stale-result rejection, and KeepAlive recreation fail.

**Step 5: Implement explicit ownership**

- Add required positive `userId` prop to ChatHistoryRail.
- Pass `userId` to list/remember calls.
- On `userId` change, invalidate the load generation, clear local turns/query/status, and reload if open.
- Apply a generation plus owner check before committing async results.
- Read `session.user.id` in WorkshopPage; do not issue history operations without an authenticated id.
- Pass the id to ChatHistoryRail and key the rail by user id.
- Key the application KeepAlive boundary by `session.user?.id ?? "anonymous"` so logout/account switch destroys all cached account-local page state.

**Step 6: Run GREEN**

Run the same three test files. Expected: all pass.

**Step 7: Run the affected workshop suite**

Run:

```bash
cd frontend
npx vitest run \
  tests/api/workshopHistory.spec.ts \
  tests/stores/session.spec.ts \
  tests/components/workshop/ChatHistoryRail.spec.ts \
  tests/pages/WorkshopPage.spec.ts \
  tests/components/workshop/WorkshopChat.spec.ts \
  src/test/App.test.ts
```

Expected: all affected tests pass.

**Step 8: Record evidence**

Append RED/GREEN and affected-suite results to the worklog.

---

### Task 4: Prove same-browser A-to-B isolation and finish the slice

**Files:**
- Create: `frontend/e2e/workshopHistoryAccountIsolation.spec.ts`
- Modify: `docs/plans/2026-07-29-incremental-refactoring-worklog.md`
- Modify: `docs/project/CHANGELOG.md`

**Step 1: Write the failing real-browser regression**

On the real local stack:

1. register two unique student accounts;
2. create one uniquely marked workshop history turn for A through the authenticated backend API;
3. log in as A in the browser and open Workshop history so the entry is cached and rendered;
4. log out in the same page;
5. log in as B and return to Workshop;
6. assert A's marker is absent and B's empty/history state is authoritative.

No route interception or frontend fake history data is allowed.

**Step 2: Run browser RED before production implementation**

Run against `./dev.sh`:

```bash
cd frontend
BASE_URL=http://127.0.0.1:5173 \
  npx playwright test e2e/workshopHistoryAccountIsolation.spec.ts \
  --project=chromium --retries=0
```

Expected before the implementation: A's cached text remains visible after switching to B.

**Step 3: Run browser GREEN after implementation**

Run the same command. Expected: 1 passed, no retries.

**Step 4: Run complete frontend verification**

Run:

```bash
cd frontend
npm run test -- --run
npm run typecheck
npm run lint
npx prettier --check \
  src/api/workshopHistory.ts \
  src/stores/session.ts \
  src/components/workshop/ChatHistoryRail.vue \
  src/pages/WorkshopPage.vue \
  src/App.vue \
  tests/api/workshopHistory.spec.ts \
  tests/stores/session.spec.ts \
  tests/components/workshop/ChatHistoryRail.spec.ts \
  tests/pages/WorkshopPage.spec.ts \
  src/test/App.test.ts \
  e2e/workshopHistoryAccountIsolation.spec.ts
```

Expected: zero failures/errors; existing lint warnings may remain only if the baseline already contains them.

**Step 5: Update records**

- Append a verified P0-1 entry to `docs/project/CHANGELOG.md`.
- Mark the slice complete in the worklog with exact counts and note that backend/Graph/RAG were
  not rerun because their code and contracts were unchanged.

**Step 6: Static verification and commit**

Run:

```bash
git diff --check
git status --short
```

Stage only task-owned files and commit:

```bash
git commit -m "refactor(frontend): isolate workshop history by account"
```
