# Agentic M3 System Evolution Gate

- Dataset: `agentic-m3-system-evolution-v1`
- Cases: 8
- Stable IDs: 8
- Result: PASS

## Candidate

- Policy: `agentic_loop_v2`
- Changed variable: `retrieval_top_k`
- Offline mean uplift: `0.16`
- Canary dry-run: `DRY_RUN_READY`
- Rollback status: `READY`

## Failure Clusters

| Cluster | Count | Loop Types | Recommended Variable |
|---|---:|---|---|
| `evidence_missing` | 3 | LEARNER, RESOURCE | `retrieval_top_k` |
| `policy_drift` | 1 | IMPROVE | `candidate_prompt_temperature` |
| `quest_evidence_gap` | 1 | WORLD | `quest_required_evidence_type` |
| `replay_gap` | 1 | DEBUG | `checkpoint_resume_cursor_required` |

## Rollback Rules

- rollback if offline replay gate fails
- rollback if schema_validity < 1.0
- rollback if regression_rate > 0.05
- rollback if cost_ratio > 1.20
