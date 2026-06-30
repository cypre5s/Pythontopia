# Pythontopia Agentic Runtime Progress

> Updated: 2026-06-30
> Current objective: execute the pasted Agentic Runtime Phase 0-9 plan from the current `Pythontopia` checkout, with real implementation, tests, changelog updates, progress docs, and local commits per phase.

## Current Phase

| Field | Value |
|---|---|
| Phase | Phase 0 - Baseline audit and execution docs |
| Status | Ready for local commit |
| Goal | Confirm repository reality, preserve compatibility constraints, initialize long-running execution docs, changelog rules, verification rules, and commit rules |
| Scope | Documentation and audit only; no runtime code changes in Phase 0 |

## Completed

### Pre-existing Agentic Baseline From Earlier Work

- M0 baseline freeze:
  - loop contract checker,
  - runtime persistence tables,
  - trace gate,
  - stronger workshop evidence gates.
- M1 learner/debug replay:
  - `ReplayPlan`,
  - `/api/loop/runs/{id}/replay`,
  - M1 golden gate.
- M2 resource/world:
  - resource publish flow,
  - world loop proposal evidence contract,
  - resource effectiveness API,
  - ingestion boundary.
- M3 system evolution:
  - stable M3 eval dataset,
  - failure clusters,
  - one-variable policy candidate,
  - offline replay gate,
  - canary dry-run scripts,
  - rollback status and policy lifecycle.

These are retained as the current implementation baseline. They do not by themselves close the new Phase 1-9 scope.

### Phase 0 Audit Work

- Read the two pasted execution-plan files.
- Read/inspected:
  - `README.md`,
  - `AGENTS.md`,
  - `docs/clients/api-contract.md`,
  - backend package layout,
  - frontend package layout,
  - graph/rag service package layouts,
  - Flyway migration tail through `V183`,
  - test directories,
  - `.github/workflows/ci.yml`,
  - build manifests: `backend/pom.xml`, `frontend/package.json`, graph/rag `pyproject.toml`.
- Confirmed current dirty worktree contains many unrelated modified files; Phase 0 must stage only docs/changelog files owned by this work.

## In Progress

- Writing `docs/agentic-runtime-plan.md`.
- Writing this progress document.
- Updating `CHANGELOG.md` with Phase 0.
- Running Phase 0 self-checks.
- Creating a local Phase 0 commit.

## Next

1. Finish and verify Phase 0 docs.
2. Commit Phase 0.
3. Start Phase 1 only after Phase 0 has:
   - changelog entry,
   - progress update,
   - verification log,
   - local commit.

## Decisions

| Decision | Reason |
|---|---|
| Treat prior M0-M3 work as baseline, not as full new-plan completion | The new pasted plan has stricter Phase 0-9 requirements, including frontend/teacher ops and full Beta handoff |
| Preserve public client contract from `docs/clients/api-contract.md` | Four client families consume the same Spring Boot API |
| Add control plane internally rather than replacing existing student/teacher APIs | Existing Tutor/Workshop/Orchestrator/Mainline flows are already user-facing and tested |
| Keep `town_hub` and no economy systems as hard constraints | R-14/R-15 explicitly removed multi-map farm/economy mainline concepts |
| Stage narrowly | Current worktree contains unrelated user/generated changes |

## Changed Files

Phase 0 intended files:

- `docs/agentic-runtime-plan.md`
- `docs/agentic-runtime-progress.md`
- `CHANGELOG.md`

## Tests

| Time | Command | Result |
|---|---|---|
| 2026-06-30 | `git diff --check` | passed |
| 2026-06-30 | `rg -n "Repository State Confirmation|Phase 0|Current Phase|Verification Matrix|Commit Rules|Agentic Runtime Phase 0" docs/agentic-runtime-plan.md docs/agentic-runtime-progress.md CHANGELOG.md` | passed: required headings and changelog entry found |
| 2026-06-30 | `git status --short -- docs/agentic-runtime-plan.md docs/agentic-runtime-progress.md CHANGELOG.md` | passed: only Phase 0 docs/changelog listed for this scope |

## Risks

- Phase 0 is documentation/audit only; it does not implement the remaining runtime phases.
- Existing unrelated dirty files must not be staged.
- Later phases need careful gap analysis to avoid duplicating or regressing existing M0-M3 runtime work.
- Full online model/provider eval remains outside local verification unless a later phase explicitly wires provider-backed gates.

## Changelog Links

- Pending Phase 0 changelog entry in `CHANGELOG.md`.

## Commits

| Phase | Commit | Notes |
|---|---|---|
| Phase 0 | pending local commit | Baseline audit docs and execution tracking |
