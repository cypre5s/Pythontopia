# AI Guide, Documentation, Release Package, and ECS Deployment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Audit and improve the Pythontopia AI learning guide against Alethicode, add a screenshot-rich in-app guide, produce a self-contained Web release archive, and deploy it behind Nginx at `http://47.120.29.62` with complete public acceptance testing.

**Architecture:** Preserve the user's dirty worktree by creating a temporary baseline commit in an isolated integration worktree, then launch ten agents once from that immutable baseline with disjoint file ownership. Merge their commits into the integration branch, run one local integration gate, generate final screenshots and release artifacts, then perform one serialized ECS switch with backup and rollback support. The public host exposes only HTTP port 80; application containers stay on loopback or the Docker network.

**Tech Stack:** Vue 3, TypeScript strict, Pinia, Vitest, Playwright, Spring Boot 3/Java 21, FastAPI/LangGraph/Python 3.11, Docker Compose, Nginx, Bash, SSH/SCP.

---

## 1. Confirmed Baseline and Non-Negotiable Constraints

- The current repository has many tracked and untracked changes. Do not stash, reset, clean, overwrite, or commit them on `main`.
- The AI learning guide currently spans:
  - `frontend/src/components/hud/GuideOverlay.vue`
  - `frontend/src/stores/guide.ts`
  - `frontend/src/api/orchestrator.ts`
  - `backend/src/main/java/com/pythontopia/orchestrator/`
  - `backend/src/main/java/com/pythontopia/tutor/`
  - `services/pythontopia-graph/pythontopia_graph/`
- Pythontopia explicitly references Alethicode's profile and tutor-graph assets, but the implementation lineage must be verified file by file rather than inferred from documentation.
- `GuideOverlay` and `WorkshopChat` already share orchestrator activity through `frontend/src/stores/workshopActivity.ts`; changes must preserve this integration.
- The archive top level must contain exactly:

```text
backend/
deploy/
frontend/
README.md
```

- Graph and RAG source directories must not be placed at archive top level. Their runtime images belong under `deploy/runtime/images/`.
- No `.env`, private key, token, password, IDE metadata, cache, `node_modules`, `target`, coverage, local video/PDF cache, or unrelated client source may enter the archive.
- Use the verified SSH identity `~/.ssh/ecs-pythontopia.pem`. Do not use `wsl.pem` or the repository copy of `wsl (1).pem`.
- The deployment target is HTTP only: `http://47.120.29.62`.
- The remote host currently has no active host Nginx. The frontend is directly exposed at `0.0.0.0:8080`, while Graph uses host networking and listens on `0.0.0.0:18091` locally. The production topology must remove both exposures.
- The ECS security group likely does not allow TCP 80: host firewall is inactive, public 8080 works, and public 80 times out. Treat security-group verification as a hard preflight gate.
- Do not enable ReAct. Do not introduce fake business data. Keep course scope fixed to `packId=43`, `Python语言基础`.

## 2. Compressed Execution Model

The implementation uses only three execution stages:

1. **One fan-out:** ten agents start together from the same baseline commit.
2. **One integration gate:** the lead agent merges, resolves conflicts, runs all local tests, captures final screenshots, and builds the archive.
3. **One release gate:** one deployment owner uploads, switches ECS, and executes public acceptance. No other agent may modify ECS concurrently.

### Frozen Cross-Agent Contracts

These rules allow the ten agents to work independently:

- Preserve the existing orchestrator request and response field names. New fields are not required for this iteration.
- Use existing `plan`, `dispatch_results`, `profile_updates`, `trace`, `panel`, `kc_codes`, and `resource_type` fields for module linkage.
- All new response variations stay inside existing textual fields; do not require Java/frontend DTO changes for style selection.
- The detailed guide route is exactly `/guide`.
- The Pause Menu item remains `id: 'help'`, but navigates to `/guide` instead of expanding the old inline help block.
- Guide screenshot URLs are frozen as:

```text
/guide/screenshots/01-town-hub.webp
/guide/screenshots/02-pause-menu.webp
/guide/screenshots/03-ai-guide.webp
/guide/screenshots/04-mainline-map.webp
/guide/screenshots/05-stage-learning.webp
/guide/screenshots/06-library.webp
/guide/screenshots/07-workshop.webp
/guide/screenshots/08-training.webp
/guide/screenshots/09-bug-mine.webp
/guide/screenshots/10-review-hall.webp
/guide/screenshots/11-profile.webp
/guide/screenshots/12-teacher-classroom.webp
```

- Screenshots must come from a real local stack and real API-backed demo account. Static fake panels are forbidden.
- The production Compose project name is always `deploy`, preserving the existing `deploy_pytopia_pg` volume.
- Host Nginx proxies to `127.0.0.1:8080`; the frontend container proxies `/api` to backend on the Docker network.

## 3. Task 0: Preserve the Dirty Baseline Without Touching Main

**Owner:** Lead agent only, before fan-out.

**Files:** No repository files changed on `main`.

**Step 1: Record the current state**

Run:

```bash
cd /home/cypress/Pythontopia
git status --short > /tmp/pythontopia-before-ai-guide.status
git rev-parse HEAD > /tmp/pythontopia-before-ai-guide.head
```

Expected: status includes the user's current modifications and untracked files.

**Step 2: Create a tracked-change snapshot without altering the worktree**

Run:

```bash
BASE_SNAPSHOT=$(git stash create "codex ai-guide baseline")
test -n "$BASE_SNAPSHOT"
printf '%s\n' "$BASE_SNAPSHOT" > /tmp/pythontopia-ai-guide-snapshot
```

Expected: a commit ID is printed; `git status --short` remains byte-for-byte unchanged.

**Step 3: Create the isolated integration worktree**

Run:

```bash
git worktree add -b codex/ai-guide-release-integration \
  /home/cypress/.worktrees/pythontopia-ai-guide-release "$BASE_SNAPSHOT"
```

Expected: the new worktree contains all tracked dirty changes captured by `git stash create`.

**Step 4: Copy only task-relevant untracked source files**

Generate and review the manifest first:

```bash
cd /home/cypress/Pythontopia
git ls-files --others --exclude-standard | sort \
  | rg -v '(^|/)(\.env|secrets|node_modules|target|dist|coverage|qa-screens)(/|$)|\.pem$' \
  > /tmp/pythontopia-ai-guide-untracked.txt
```

Copy only files needed by the tutor/workshop work already in progress. Never copy `.pem`, `.env`, generated media, PDFs, videos, or credentials.

**Step 5: Commit the temporary baseline in the isolated branch**

Run:

```bash
cd /home/cypress/.worktrees/pythontopia-ai-guide-release
git add -A
git commit -m "chore: preserve ai guide release baseline"
git rev-parse HEAD > /tmp/pythontopia-ai-guide-baseline
```

Expected: a clean temporary branch. This commit is never merged wholesale into `main`.

**Step 6: Verify main was untouched**

Run:

```bash
cd /home/cypress/Pythontopia
diff -u /tmp/pythontopia-before-ai-guide.status <(git status --short)
```

Expected: no diff.

## 4. Task 1: Launch the Ten-Agent Fan-Out

**Owner:** Lead agent.

**Required skills:** `@dispatching-parallel-agents`, `@subagent-driven-development`.

Create ten branches/worktrees from the commit in `/tmp/pythontopia-ai-guide-baseline`. Each agent must commit only its owned files and return its commit hash, tests, findings, and unresolved risks.

Run the worktree creation in parallel:

```bash
BASELINE=$(cat /tmp/pythontopia-ai-guide-baseline)
for n in 01 02 03 04 05 06 07 08 09 10; do
  git -C /home/cypress/Pythontopia worktree add \
    -b "codex/ai-guide-agent-$n" \
    "/home/cypress/.worktrees/pythontopia-ai-guide-$n" \
    "$BASELINE"
done
```

Expected: ten clean worktrees at the same baseline.

### Agent 1: Alethicode Lineage and Gap Audit

**Write ownership:**

- Create: `docs/reports/ai-guide-alethicode-lineage.md`
- Create: `docs/reports/ai-guide-gap-matrix.md`

**Read scope:** Pythontopia tutor/orchestrator files plus:

- `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/`
- `/home/cypress/Alethicode/services/tutor-graph/app/`
- `/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`

**Prompt:**

```text
Audit whether Pythontopia's AI learning guide is genuinely derived from or only inspired by Alethicode's AI tutor. Build a file-by-file lineage matrix covering profile context, conversation memory, evidence/RAG, diagnosis, safety, grounding, learning path, execution feedback, visualization, failure handling, observability, and frontend interaction. Classify every row as reused/adapted/reimplemented/missing/not-applicable and cite exact paths and symbols. Then write a prioritized P0/P1 gap matrix focused on pack 43 localization, robustness, expression diversity, and linkage with Baixiao Workshop. Do not edit production code. Do not claim semantic equivalence without code evidence.
```

**Verification:**

```bash
rg -n "reused|adapted|reimplemented|missing|not-applicable" docs/reports/ai-guide-*.md
rg -n "/home/cypress/Alethicode|frontend/src|backend/src|services/pythontopia-graph" docs/reports/ai-guide-*.md
```

Expected: every material claim has source paths; no generic architecture-only conclusion.

**Commit:**

```bash
git add docs/reports/ai-guide-alethicode-lineage.md docs/reports/ai-guide-gap-matrix.md
git commit -m "docs: audit AI guide Alethicode lineage"
```

### Agent 2: Graph Localization, Diversity, and Deterministic Robustness

**Write ownership:**

- Modify: `services/pythontopia-graph/pythontopia_graph/agents/orchestrator_agent.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/agents/tutor_agent.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/graph/orchestrator_graph.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/nodes/tutor_review.py`
- Modify/create focused tests under `services/pythontopia-graph/tests/test_*tutor*` and `test_orchestrator_agent.py`

**Do not edit:** Java, Vue, Compose, deployment files, shared schemas unless a failing existing test proves an implementation defect. Preserve current response schema.

**Step 1: Write failing tests**

Add tests proving:

- pack 43 and beginner Python context are explicit in prompts and deterministic fallbacks;
- two adjacent turns do not always reuse the same opening, explanation structure, or closing;
- style selection respects profile signals such as `cognitive_style`, `learning_preference`, and `emotion_persistence`;
- ambiguous requests ask one concise question instead of dumping a generic plan;
- unavailable providers produce a structured deterministic educational response, never free-form silent chat fallback;
- safety and grounding still run after style variation;
- direct answer leakage remains blocked.

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q \
  tests/test_orchestrator_agent.py \
  tests/test_tutor_agent_cognitive_style.py \
  tests/test_tutor_agent_personalization.py \
  tests/test_tutor_review.py
```

Expected: new assertions fail before implementation.

**Step 2: Implement minimal behavior**

Use a bounded style catalog, selected deterministically from session/turn/profile context. Examples of internal modes may include `step_by_step`, `analogy_first`, `example_first`, `question_first`, and `encouraging_recap`; the public response must remain natural Chinese, not expose mode identifiers, and not fabricate course evidence.

**Step 3: Run focused and full graph tests**

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_orchestrator_agent.py tests/test_tutor_agent_cognitive_style.py tests/test_tutor_agent_personalization.py tests/test_tutor_review.py
python -m pytest -q
```

Expected: all tests pass.

**Commit:**

```bash
git add services/pythontopia-graph
git commit -m "feat(graph): localize and diversify AI guide responses"
```

### Agent 3: Java Tutor and Orchestrator Failure Hardening

**Write ownership:**

- Modify: `backend/src/main/java/com/pythontopia/orchestrator/`
- Modify: `backend/src/main/java/com/pythontopia/tutor/`
- Modify: `backend/src/main/resources/application.yml` only for bounded tutor/orchestrator configuration
- Modify/create tests under `backend/src/test/java/com/pythontopia/orchestrator/` and `backend/src/test/java/com/pythontopia/tutor/`

**Do not edit:** frontend, Python, migrations, unrelated backend packages.

**Step 1: Add failing tests for failure classes**

Cover:

- graph connection failure;
- graph read timeout;
- malformed or prematurely closed SSE;
- client disconnect cleanup;
- concurrent duplicate submission from one session;
- preservation of partial SSE content before a terminal error;
- no leakage of internal URL, stack trace, token, or credentials to the client.

Run:

```bash
cd backend
mvn -q -Dtest='Orchestrator*Test,Tutor*Test' test
```

Expected: new tests fail.

**Step 2: Implement bounded handling**

- Reuse the repository's existing timeout configuration and unified error response conventions.
- Fail fast when Graph is unavailable.
- Emit one terminal SSE error and close resources exactly once.
- Keep user-facing Chinese messages actionable and short.
- Do not silently replace an agent failure with ordinary chat.

**Step 3: Verify**

```bash
cd backend
mvn -q -Dtest='Orchestrator*Test,Tutor*Test' test
mvn -q test
```

Expected: all backend tests pass.

**Commit:**

```bash
git add backend/src/main/java/com/pythontopia/orchestrator \
        backend/src/main/java/com/pythontopia/tutor \
        backend/src/main/resources/application.yml \
        backend/src/test/java/com/pythontopia/orchestrator \
        backend/src/test/java/com/pythontopia/tutor
git commit -m "fix(backend): harden AI guide streaming failures"
```

### Agent 4: Guide Overlay Conversation Experience

**Write ownership:**

- Modify: `frontend/src/components/hud/GuideOverlay.vue`
- Modify: `frontend/src/stores/guide.ts`
- Modify: `frontend/src/api/orchestrator.ts` only if types are inaccurate relative to existing fields
- Create: `frontend/tests/components/hud/GuideOverlay.spec.ts`
- Create: `frontend/tests/stores/guide.spec.ts`

**Do not edit:** workshop files, router, Pause Menu, guide documentation page, package files.

**Step 1: Write failing tests**

Cover:

- one active request at a time;
- partial answer remains visible after stream failure;
- retry resends the failed user turn without duplicating history;
- closing and reopening preserves session history;
- plan steps navigate only to allowlisted internal panels;
- reduced-motion and keyboard interaction;
- status/error text is announced accessibly;
- suggestions vary with conversation state instead of remaining static.

Run:

```bash
cd frontend
npx vitest run tests/components/hud/GuideOverlay.spec.ts tests/stores/guide.spec.ts
```

Expected: tests fail before implementation.

**Step 2: Implement the minimal UX changes**

Keep the panel compact. Preserve streamed Markdown and traces. Add retry and clear failure states without introducing a second chat implementation.

**Step 3: Verify**

```bash
cd frontend
npm run typecheck
npx vitest run tests/components/hud/GuideOverlay.spec.ts tests/stores/guide.spec.ts
```

Expected: typecheck and focused tests pass.

**Commit:**

```bash
git add frontend/src/components/hud/GuideOverlay.vue \
        frontend/src/stores/guide.ts \
        frontend/src/api/orchestrator.ts \
        frontend/tests/components/hud/GuideOverlay.spec.ts \
        frontend/tests/stores/guide.spec.ts
git commit -m "feat(web): improve AI guide conversation resilience"
```

### Agent 5: Baixiao Workshop and Learning-Module Linkage

**Write ownership:**

- Modify: `frontend/src/components/workshop/WorkshopChat.vue`
- Modify: `frontend/src/stores/workshopActivity.ts`
- Modify: `frontend/src/components/workshop/PlanCard.vue`
- Modify/create: `frontend/tests/components/workshop/WorkshopChat.spec.ts`
- Create: `frontend/tests/stores/workshopActivity.spec.ts`

**Do not edit:** `GuideOverlay.vue`, router, Python, Java, package files.

**Step 1: Write failing tests**

Cover:

- orchestrator activities started in the companion guide appear in Workshop activity state;
- existing activity is not overwritten by a stale stream;
- plan actions deep-link to Workshop with `kc` and resource context where available;
- practice, library, review, and training links preserve KC context;
- returning from Workshop does not duplicate the guide plan;
- stale session data is ignored after a new activity starts.

Run:

```bash
cd frontend
npx vitest run tests/components/workshop/WorkshopChat.spec.ts tests/stores/workshopActivity.spec.ts
```

Expected: new tests fail.

**Step 2: Implement linkage using existing fields**

Do not add a new cross-service DTO. Use `plan[].panel`, `plan[].kc_codes`, `resource_type`, and the existing shared activity store.

**Step 3: Verify**

```bash
cd frontend
npm run typecheck
npx vitest run tests/components/workshop/WorkshopChat.spec.ts tests/stores/workshopActivity.spec.ts
```

Expected: typecheck and focused tests pass.

**Commit:**

```bash
git add frontend/src/components/workshop/WorkshopChat.vue \
        frontend/src/components/workshop/PlanCard.vue \
        frontend/src/stores/workshopActivity.ts \
        frontend/tests/components/workshop/WorkshopChat.spec.ts \
        frontend/tests/stores/workshopActivity.spec.ts
git commit -m "feat(workshop): link AI guide plans to learning modules"
```

### Agent 6: Claude-Code-Docs-Style In-App Guide

**Required skills:** `@frontend-design`, `@ui-ux-pro-max`.

**Write ownership:**

- Create: `frontend/src/pages/GuidePage.vue`
- Create: `frontend/src/components/guide/GuideSidebar.vue`
- Create: `frontend/src/components/guide/GuideSection.vue`
- Create: `frontend/src/components/guide/GuideScreenshot.vue`
- Create: `frontend/src/content/guideSections.ts`
- Create: `frontend/tests/pages/GuidePage.spec.ts`
- Modify: `frontend/src/app/router.ts`
- Modify: `frontend/src/components/hud/PauseMenu.vue`

**Do not edit:** screenshot binaries, package files, guide overlay, workshop, global styles unless unavoidable. Prefer component-scoped styles and existing tokens.

**Step 1: Write failing tests**

Tests must prove:

- `/guide` is an authenticated route;
- Pause Menu `操作帮助` navigates to `/guide`;
- desktop layout has left navigation, readable article column, and right on-page outline;
- mobile layout collapses navigation without hiding content;
- every screenshot has meaningful alt text and a caption;
- section anchors can be linked directly;
- keyboard focus and active-section state work;
- content covers town controls, mainline, AI guide, library, Workshop, training, Bug Mine, review, profile, and teacher classroom.

Run:

```bash
cd frontend
npx vitest run tests/pages/GuidePage.spec.ts tests/app/router.spec.ts
```

Expected: new route and page tests fail.

**Step 2: Implement the page**

Use a restrained documentation layout inspired by Claude Code Docs information architecture, not its branding. Prioritize 16px-or-larger body text, generous line height, clear headings, stable anchors, copyable text, and strong contrast. Pixel styling is limited to borders, icons, labels, and decorative corners.

**Step 3: Reference the frozen screenshot URLs**

The page must render gracefully if an image is temporarily unavailable during development, but the release gate forbids missing images.

**Step 4: Verify**

```bash
cd frontend
npm run typecheck
npx vitest run tests/pages/GuidePage.spec.ts tests/app/router.spec.ts
```

Expected: all pass.

**Commit:**

```bash
git add frontend/src/pages/GuidePage.vue \
        frontend/src/components/guide \
        frontend/src/content/guideSections.ts \
        frontend/src/app/router.ts \
        frontend/src/components/hud/PauseMenu.vue \
        frontend/tests/pages/GuidePage.spec.ts \
        frontend/tests/app/router.spec.ts
git commit -m "feat(web): add screenshot-rich learning guide"
```

### Agent 7: Reproducible Screenshot Capture Infrastructure

**Required skill:** `@webapp-testing`.

**Write ownership:**

- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/playwright.config.ts`
- Create: `frontend/e2e/guideScreenshots.spec.ts`
- Create: `frontend/e2e/support/demoSession.ts`
- Create: `frontend/public/guide/screenshots/.gitkeep`
- Create: `docs/guide/screenshot-manifest.md`

**Do not edit:** guide page components or final screenshot binaries during fan-out.

**Step 1: Add Playwright and scripts**

Required scripts:

```json
{
  "test:e2e": "playwright test",
  "capture:guide": "playwright test e2e/guideScreenshots.spec.ts --project=chromium"
}
```

Scripts run from `frontend/`.

**Step 2: Implement real-stack authentication and capture**

- Use API registration/login or a seeded demo account.
- Never store credentials in source.
- Capture the twelve frozen filenames at a consistent 1440x900 viewport.
- Hide or mask tokens, email addresses, timestamps, and personal identifiers.
- Wait on stable UI conditions, not arbitrary long sleeps.
- Convert captures to WebP with readable text; do not over-compress.

**Step 3: Add manifest validation**

The test must fail if any required screen cannot be reached or if the output file is empty.

**Step 4: Verify infrastructure**

```bash
cd frontend
npm ci
npx playwright install chromium
npx playwright test --list
```

Expected: screenshot suite is discovered. It may be deferred until the integrated local stack is running.

**Commit:**

```bash
git add frontend/package.json frontend/package-lock.json frontend/playwright.config.ts \
        frontend/e2e frontend/public/guide/screenshots/.gitkeep \
        docs/guide/screenshot-manifest.md
git commit -m "test(web): add reproducible guide screenshot capture"
```

### Agent 8: Self-Contained Release Archive Builder

**Write ownership:**

- Create: `deploy/release/README.md`
- Create: `deploy/release/archive-allowlist.txt`
- Create: `deploy/scripts/build_release_archive.sh`
- Create: `deploy/scripts/verify_release_archive.sh`
- Modify: `frontend/nginx.conf`

**Do not edit:** production Compose or host Nginx files owned by Agent 9.

**Step 1: Write shell-level failing checks**

The verifier must reject:

- any unexpected top-level entry;
- `.env`, `.pem`, token/key-like file, `node_modules`, `target`, `dist`, coverage, Git metadata, PDFs, videos, generated media, Android, Windows, or WeChat source;
- missing Graph/RAG runtime images;
- missing root `README.md`;
- checksum mismatch.

**Step 2: Implement the builder**

The builder must:

1. build frontend, backend, graph, and RAG images from the integrated checkout;
2. export application images under `deploy/runtime/images/`;
3. stage only `frontend/`, `backend/`, `deploy/`, and a package-root `README.md` copied from `deploy/release/README.md`;
4. remove local-only files according to the allowlist;
5. write `deploy/runtime/sha256sums.txt`;
6. create `output/packages/pythontopia-web-<UTC timestamp>.tar.gz`;
7. verify the archive before returning success.

Use temporary directories and `trap` cleanup. Do not mutate source directories while staging.

**Step 3: Harden container Nginx for SSE**

In `frontend/nginx.conf`, ensure `/api/` uses:

```nginx
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 300s;
proxy_send_timeout 300s;
```

Preserve SPA fallback and health endpoint.

**Step 4: Verify**

```bash
bash -n deploy/scripts/build_release_archive.sh
bash -n deploy/scripts/verify_release_archive.sh
deploy/scripts/verify_release_archive.sh /path/to/test/archive.tar.gz
```

Expected: verifier passes only for the exact archive shape.

**Commit:**

```bash
git add deploy/release deploy/scripts/build_release_archive.sh \
        deploy/scripts/verify_release_archive.sh frontend/nginx.conf
git commit -m "build: add self-contained web release archive"
```

### Agent 9: Production Compose, Host Nginx, Install, and Rollback

**Write ownership:**

- Create: `deploy/docker-compose.production.yml`
- Create: `deploy/nginx/pythontopia.conf`
- Create: `deploy/scripts/install_release.sh`
- Create: `deploy/scripts/rollback_release.sh`
- Create: `deploy/scripts/preflight_ecs.sh`
- Update: `deploy/README.md`

**Do not edit:** `frontend/nginx.conf`, archive builder, application code.

**Step 1: Define the production topology**

- no build contexts;
- frontend image binds `127.0.0.1:8080:80`;
- backend may bind `127.0.0.1:18080:18080` for host diagnostics only;
- Graph and RAG expose no host ports;
- Postgres and Redis expose no host ports;
- Graph uses the Compose bridge network, not `network_mode: host`;
- backend connects to Graph/RAG by service name;
- health checks cover all six services;
- `restart: unless-stopped` is explicit;
- secrets come from the existing remote `.env`, never from the archive.

**Step 2: Create host Nginx configuration**

Listen on port 80 for `47.120.29.62` and proxy all traffic to `127.0.0.1:8080`. Forward standard headers, disable buffering for streamed API responses, set sensible body limits, and keep `/healthz` reachable.

**Step 3: Implement preflight**

Check:

- Ubuntu version, root access, disk space, memory, Docker, Compose;
- current Compose project and volume names;
- `.env` existence without printing values;
- required AI provider variable names are set without revealing values;
- TCP 80 availability;
- whether Aliyun CLI credentials can inspect or modify the security group.

If host port 80 works locally but public port 80 remains unreachable, stop before disabling 8080 and report `SECURITY_GROUP_PORT_80_BLOCKED`.

**Step 4: Implement transactional install**

Use `/opt/pythontopia/releases/<timestamp>`, `/opt/pythontopia/backups/`, and `/opt/pythontopia/current`. Before switching:

- dump Postgres with `pg_dump` and gzip;
- back up remote `.env`, Compose overrides, and Nginx configuration;
- load image tar files and verify checksums;
- run `docker compose -p deploy -f deploy/docker-compose.production.yml config`;
- start dependencies and app services;
- wait for health checks;
- install and validate Nginx with `nginx -t`;
- switch the `current` symlink atomically;
- reload Nginx;
- retain the previous release for rollback.

**Step 5: Implement rollback**

Rollback must restore the previous release and Nginx config without deleting the Postgres volume. Database restoration is manual and only used if a migration is proven incompatible.

**Step 6: Verify scripts**

```bash
bash -n deploy/scripts/preflight_ecs.sh
bash -n deploy/scripts/install_release.sh
bash -n deploy/scripts/rollback_release.sh
docker compose -p deploy -f deploy/docker-compose.production.yml config >/tmp/pythontopia-production-compose.yml
```

Expected: valid shell and Compose configuration.

**Commit:**

```bash
git add deploy/docker-compose.production.yml deploy/nginx \
        deploy/scripts/preflight_ecs.sh deploy/scripts/install_release.sh \
        deploy/scripts/rollback_release.sh deploy/README.md
git commit -m "ops: add transactional ECS deployment"
```

### Agent 10: Public Acceptance and Security Test Suite

**Required skill:** `@webapp-testing`.

**Write ownership:**

- Create: `frontend/e2e/publicAcceptance.spec.ts`
- Create: `deploy/scripts/public_acceptance.sh`
- Create: `docs/testing/public-web-acceptance.md`

**Do not edit:** package files, Playwright config, deployment implementation, application code.

**Step 1: Define browser acceptance**

Cover:

- registration/login and authenticated redirect;
- town hub load with no fatal console errors;
- `/guide` navigation, anchors, image zoom, desktop and mobile layouts;
- AI guide multi-turn SSE, clarification, plan rendering, retry after an induced failure;
- AI guide to Workshop deep-link with KC context;
- Workshop real response path;
- library RAG query for `input函数`, `for循环`, and `列表`, including evidence pages;
- training pass, fail, and runtime-error submissions;
- review hall and profile visibility;
- direct refresh of `/guide`, `/workshop`, and `/mainline-map`;
- no secret, stack trace, or internal service hostname in rendered errors.

**Step 2: Define external shell acceptance**

The shell script must verify:

- HTTP 80 returns 200;
- `/healthz` returns 200;
- static assets have expected cache/content headers;
- 8080, 18080, 18091, 18092, 15432, and 16379 are not publicly reachable;
- no redirect points back to `:8080`;
- repeated SSE requests do not return 502/504;
- archive and deployed release checksums match.

**Step 3: Document evidence collection**

Record command output, browser screenshots, console errors, failed requests, tested account IDs, and cleanup steps. Store runtime evidence in ignored `qa-screens/` and a timestamped local report; do not commit credentials or personal data.

**Step 4: Static verification**

```bash
bash -n deploy/scripts/public_acceptance.sh
cd frontend
npx playwright test --list
```

Expected: browser and shell suites are discoverable and syntactically valid.

**Commit:**

```bash
git add frontend/e2e/publicAcceptance.spec.ts \
        deploy/scripts/public_acceptance.sh \
        docs/testing/public-web-acceptance.md
git commit -m "test: add public web acceptance suite"
```

## 5. Task 2: Single Integration Gate

**Owner:** Lead agent only.

**Required skills:** `@receiving-code-review`, `@requesting-code-review`, `@verification-before-completion`.

### Step 1: Review every agent result before merging

For each commit:

```bash
git show --stat --oneline <commit>
git diff --check <baseline>..<commit>
```

Reject commits that touch files outside ownership, introduce secrets, weaken tests, enable ReAct, add fake data, or alter unrelated modules.

### Step 2: Merge commits in dependency order

In `/home/cypress/.worktrees/pythontopia-ai-guide-release`:

```bash
git cherry-pick <agent-1-commit>
git cherry-pick <agent-2-commit>
git cherry-pick <agent-3-commit>
git cherry-pick <agent-4-commit>
git cherry-pick <agent-5-commit>
git cherry-pick <agent-6-commit>
git cherry-pick <agent-7-commit>
git cherry-pick <agent-8-commit>
git cherry-pick <agent-9-commit>
git cherry-pick <agent-10-commit>
```

Expected: no overlapping ownership conflicts. If a conflict appears, resolve according to the frozen contracts, not by dropping either feature silently.

### Step 3: Run independent code reviews in parallel

Dispatch four read-only reviewers:

1. AI tutor correctness, safety, grounding, and Alethicode lineage.
2. Vue UX, accessibility, and Workshop linkage.
3. packaging, secret exclusion, Docker, Nginx, and rollback.
4. test coverage and public acceptance completeness.

Fix all P0/P1 findings before proceeding.

### Step 4: Run full local verification

Backend:

```bash
cd backend
mvn clean test
```

Graph:

```bash
cd services/pythontopia-graph
python -m pytest -q
```

RAG regression:

```bash
cd services/pythontopia-rag
python -m pytest -q
```

Frontend:

```bash
cd frontend
npm ci
npm run typecheck
npm run lint
npm run test
npm run build
npx playwright install chromium
```

Expected: every command exits 0. Do not accept pre-existing failures without identifying and documenting them.

### Step 5: Start the real integrated stack

```bash
cd /home/cypress/.worktrees/pythontopia-ai-guide-release
./dev.sh
curl -fsS http://127.0.0.1:5173/healthz
docker compose -f deploy/docker-compose.yml ps
```

Expected: frontend, backend, Graph, RAG, Postgres, and Redis are available.

### Step 6: Capture final screenshots

```bash
cd frontend
npm run capture:guide
```

Verify exactly twelve WebP files exist and inspect each visually. Reject screenshots with loading indicators, broken assets, private data, clipped dialogs, fake responses, or unreadable text.

Run:

```bash
find public/guide/screenshots -maxdepth 1 -type f -name '*.webp' -size +20k | sort
```

Expected: all twelve frozen filenames.

Commit only the final screenshot binaries:

```bash
git add frontend/public/guide/screenshots
git commit -m "docs(web): add verified guide screenshots"
```

### Step 7: Run local browser acceptance

```bash
cd frontend
BASE_URL=http://127.0.0.1:5173 npx playwright test
```

Expected: guide, AI tutor, Workshop linkage, deep refresh, responsive, and console checks pass.

### Step 8: Build and verify the release archive

```bash
cd /home/cypress/.worktrees/pythontopia-ai-guide-release
deploy/scripts/build_release_archive.sh
ARCHIVE=$(find output/packages -maxdepth 1 -type f -name 'pythontopia-web-*.tar.gz' -printf '%T@ %p\n' | sort -nr | head -n1 | cut -d' ' -f2-)
deploy/scripts/verify_release_archive.sh "$ARCHIVE"
tar -tzf "$ARCHIVE" | awk -F/ 'NF {print $1}' | sort -u
sha256sum "$ARCHIVE" > "$ARCHIVE.sha256"
```

Expected top-level output:

```text
README.md
backend
deploy
frontend
```

### Step 9: Apply only feature changes back to the user's dirty worktree

Never merge the temporary baseline commit into `main`.

Create a feature-only patch:

```bash
cd /home/cypress/.worktrees/pythontopia-ai-guide-release
BASELINE=$(cat /tmp/pythontopia-ai-guide-baseline)
git diff --binary "$BASELINE"..HEAD > /tmp/pythontopia-ai-guide-feature.patch
```

Dry-run against the original worktree:

```bash
cd /home/cypress/Pythontopia
git apply --check /tmp/pythontopia-ai-guide-feature.patch
```

If and only if the check succeeds:

```bash
git apply /tmp/pythontopia-ai-guide-feature.patch
```

Expected: original user changes remain, and only feature deltas are added. If the check fails because the user changed overlapping files during execution, stop and reconcile manually; never overwrite.

## 6. Task 3: Single ECS Release Gate

**Owner:** One deployment agent only. All other agents remain read-only.

### Step 1: Establish the direct SSH route

Discover the non-TUN default route rather than hard-coding the old hotspot gateway:

```bash
REAL_ROUTE=$(ip route show default | awk '$3 != "198.18.0.2" {print $3, $5; exit}')
REAL_GW=$(awk '{print $1}' <<<"$REAL_ROUTE")
REAL_DEV=$(awk '{print $2}' <<<"$REAL_ROUTE")
sudo ip route replace 47.120.29.62/32 via "$REAL_GW" dev "$REAL_DEV"
ip route get 47.120.29.62
ssh -o BatchMode=yes -o ConnectTimeout=12 ecs-pythontopia true
```

Expected: route does not use `198.18.0.2`; SSH succeeds with `~/.ssh/ecs-pythontopia.pem`.

### Step 2: Run read-only remote preflight

```bash
ssh ecs-pythontopia 'bash -s' < deploy/scripts/preflight_ecs.sh
```

Expected: sufficient disk, Docker/Compose healthy, current volume identified, remote `.env` found, and no secret values printed.

### Step 3: Resolve the TCP 80 security-group gate

First install a temporary loopback-safe HTTP responder or Nginx and verify remote-local port 80. Then test externally with proxies bypassed:

```bash
curl --noproxy '*' --connect-timeout 5 --max-time 10 http://47.120.29.62/
```

If remote-local port 80 works but public access times out:

- use configured Aliyun CLI credentials to add an inbound TCP 80 rule scoped to `0.0.0.0/0`; or
- stop with `SECURITY_GROUP_PORT_80_BLOCKED` and obtain the rule through the Aliyun console.

Do not disable the current 8080 route until public port 80 is confirmed reachable.

### Step 4: Upload the frozen artifact

```bash
scp "$ARCHIVE" "$ARCHIVE.sha256" ecs-pythontopia:/opt/pythontopia/incoming/
```

Expected: remote SHA-256 matches local.

### Step 5: Install transactionally

```bash
ssh -t ecs-pythontopia \
  "/opt/pythontopia/current/deploy/scripts/install_release.sh /opt/pythontopia/incoming/$(basename "$ARCHIVE")"
```

If the script is not yet installed, extract only the installer from the archive to a temporary directory and execute it there.

Expected:

- database backup completed;
- all image checksums verified;
- `docker compose -p deploy ... ps` shows six healthy services;
- host Nginx is active and `nginx -t` passes;
- `curl http://127.0.0.1/healthz` returns 200;
- frontend container binds only `127.0.0.1:8080`;
- Graph/RAG/DB/Redis have no public host bindings.

### Step 6: Run public acceptance

Shell checks:

```bash
deploy/scripts/public_acceptance.sh http://47.120.29.62
```

Browser checks:

```bash
cd frontend
BASE_URL=http://47.120.29.62 npx playwright test e2e/publicAcceptance.spec.ts
```

Manual external port verification:

```bash
for port in 80 8080 18080 18091 18092 15432 16379; do
  nc -zvw3 47.120.29.62 "$port" && echo "OPEN $port" || echo "CLOSED $port"
done
```

Expected: only 80 is reachable.

### Step 7: Exercise the competition demo chain

Verify in a real browser:

1. Login or register a temporary QA student.
2. Enter `town_hub`.
3. Open the new `/guide` from Pause Menu.
4. Ask the AI guide two differently worded questions and confirm non-repetitive but grounded replies.
5. Follow an AI guide plan into Baixiao Workshop with KC context preserved.
6. Query RAG for `input函数`, `for循环`, and `列表`; verify evidence pages.
7. Submit passing, wrong-answer, and runtime-error Python solutions.
8. Confirm failure can lead to Bug Mine without exposing hidden tests.
9. Open review hall and profile to confirm cross-module updates.
10. Refresh `/guide`, `/workshop`, and `/mainline-map` directly.

Expected: no fatal console errors, no 502/504, no broken images, no redirect to `:8080`, and no fake-data-only path.

### Step 8: Roll back on any release-blocking failure

Release-blocking conditions include:

- public 80 unavailable;
- any service unhealthy;
- login or main page broken;
- AI guide or Workshop repeatedly 502/504;
- database migration error;
- internal ports publicly reachable;
- missing guide screenshots or broken route refresh.

Run:

```bash
ssh -t ecs-pythontopia '/opt/pythontopia/current/deploy/scripts/rollback_release.sh'
```

Then rerun the previous release's smoke test and record the failure evidence.

### Step 9: Remove the temporary local route

```bash
sudo ip route del 47.120.29.62/32 via "$REAL_GW" dev "$REAL_DEV" || true
ip route get 47.120.29.62
```

Expected: route returns to the user's original networking state.

## 7. Final Acceptance Checklist

### AI Learning Guide

- [ ] File-level Alethicode lineage report exists and is evidence-backed.
- [ ] Pack 43 and beginner Python localization is explicit.
- [ ] Adjacent turns exhibit controlled expression diversity.
- [ ] Profile dimensions influence explanation style without stereotyping.
- [ ] SSE failures are bounded, visible, retryable, and do not leak internals.
- [ ] Safety, grounding, evidence, and answer-leak prevention remain active.
- [ ] Guide plans deep-link into Workshop/library/training/review with KC context.

### Detailed Guide

- [ ] Pause Menu opens `/guide`.
- [ ] Documentation layout is readable before decorative pixel styling.
- [ ] All twelve real screenshots load with captions and alt text.
- [ ] Desktop, tablet, and mobile layouts pass.
- [ ] Anchors and direct route refresh work.

### Archive

- [ ] Archive top level contains only `frontend`, `backend`, `deploy`, and `README.md`.
- [ ] Graph/RAG runtime images are under `deploy/runtime/images`.
- [ ] SHA-256 verification passes.
- [ ] Secret scan passes.
- [ ] README explains structure, prerequisites, deployment, health checks, backup, and rollback.

### ECS Deployment

- [ ] Public URL is exactly `http://47.120.29.62` without `:8080`.
- [ ] Host Nginx owns port 80.
- [ ] Only port 80 is externally reachable.
- [ ] Six services are healthy.
- [ ] Database backup exists before switch.
- [ ] Previous release remains rollback-capable.

### Public Test Evidence

- [ ] Shell acceptance passes.
- [ ] Playwright public acceptance passes.
- [ ] Browser console has no release-blocking errors.
- [ ] RAG queries return real citations.
- [ ] Judge pass/fail/runtime-error paths work.
- [ ] AI guide to Workshop linkage works publicly.
- [ ] Temporary QA accounts are removed after testing.

## 8. Delivery Report Format

The final execution report must contain:

1. What changed, grouped by AI guide, guide page, packaging, and deployment.
2. Alethicode lineage conclusion with precise proof boundaries.
3. Local test commands and results.
4. Archive path, size, and SHA-256.
5. ECS release directory and backup path.
6. Public URL and externally reachable ports.
7. Public browser scenarios and results.
8. Anything not verified and why.
9. Remaining risks, especially security-group ownership, HTTP-only transport, 2C4G capacity, and external model-provider availability.
10. Whether the competition demo chain is affected.
