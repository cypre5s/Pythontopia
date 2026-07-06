# Agentic Runtime 100+ E2E Deep-Chain Results

> Date: 2026-07-01 11:30 CST
> Command: `PYT_E2E_REGISTER=1 npx playwright test e2e/agenticDeepLoop.spec.ts e2e/agenticDeepMatrix.spec.ts --project=chromium --workers=1`

## Overall Result

| Suite | Scope | Tests | Passed | Failed | Duration | Result |
|---|---|---:|---:|---:|---:|---|
| `agenticDeepLoop.spec.ts` | Full student learning loop with API and browser clicks | 32 | 32 | 0 | included | PASS |
| `agenticDeepMatrix.spec.ts` | 100+ fine-grained deep-chain matrix | 153 | 153 | 0 | included | PASS |
| Total | Combined E2E run | 185 | 185 | 0 | 3.4m | PASS |

## Environment Checks

| Endpoint | Result |
|---|---|
| `http://127.0.0.1:18080/healthz` | `{"status":"ok"}` |
| `http://127.0.0.1:18091/healthz` | `{"status":"ok"}` |
| `http://127.0.0.1:18092/healthz` | `{"status":"ok"}` |
| `http://127.0.0.1:5173/healthz` | `{"status":"ok"}` |

## 153-Test Matrix Breakdown

| Group | Range | Count | Result |
|---|---:|---:|---|
| Session | M001-M003 | 3 | PASS |
| Bootstrap | M004-M015 | 12 | PASS |
| World compatibility | M016 | 1 | PASS |
| Runtime run | M017-M026 | 10 | PASS |
| Runtime snapshot | M027-M040 | 14 | PASS |
| Replay | M041-M046 | 6 | PASS |
| Runtime summary | M047-M054 | 8 | PASS |
| Runtime operations | M055-M065 | 11 | PASS |
| Learner memory | M066-M073 | 8 | PASS |
| Evidence graph | M074-M078 | 5 | PASS |
| Resource detail | M079-M090 | 12 | PASS |
| Resource consumption | M091-M096 | 6 | PASS |
| Micro-check | M097-M100 | 4 | PASS |
| Resource effectiveness | M101-M107 | 7 | PASS |
| Remediation and resource swap | M108-M115 | 8 | PASS |
| Formal judge evidence | M116-M125 | 10 | PASS |
| Submissions | M126-M133 | 8 | PASS |
| Bug Mine debug loop | M134-M141 | 8 | PASS |
| Quests | M142-M144 | 3 | PASS |
| Recommendations | M145 | 1 | PASS |
| Browser workshop UI | M146-M153 | 8 | PASS |

## Deep-Chain Coverage

| Chain Segment | Evidence In This Run |
|---|---|
| Auth/session | Temporary student registration and JWT-backed requests |
| Startup | `POST /api/game/bootstrap` and legacy `/api/world/session` |
| Runtime control plane | bounded loop run, checkpoints, tool calls, agent runs, eval result, snapshot, replay |
| Runtime visibility | `/api/ai/runtime/summary` and `/api/ai/runtime/operations` |
| Learner memory/evidence | memory brief and evidence graph |
| Resource loop | resource generation, list/detail, reading consumption, micro-check, effectiveness |
| Formal judge boundary | failed and passed service-side submissions, KC mismatch rejection |
| Debug loop | Bug Mine session, locked card behavior, retry submission |
| Quest/recommendation | today quests and learner recommendations remain readable after events |
| Browser UI | workshop page, call drawer, runtime operations, runtime summary, study drawer, resource modal, loop rail, micro-check input |

## Notes

- The matrix uses real backend, graph, RAG, database, Vite, and Chromium browser execution.
- The API matrix is intentionally fine-grained: it validates stable sub-results from one full chain instead of generating 153 unrelated accounts or resources.
- External production canary traffic and online provider-quality evaluation remain outside this local E2E run.
