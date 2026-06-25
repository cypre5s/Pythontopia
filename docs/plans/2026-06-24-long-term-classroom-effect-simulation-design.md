# Long-Term Classroom Effect Simulation Design

## Goal

Simulate the long-term learning-effect checks that still need real classroom logs: KC mastery, review completion rate, and resource usage rate. The simulation should run against the real database schema so it can become a regression gate and a template for future production analytics.

## Design

Use a deterministic offline A/B cohort simulation in backend tests:

- Create one teacher, one classroom, and two equal student cohorts.
- Seed the same KC set for both cohorts.
- Baseline cohort receives sparse resource consumption, fewer review completions, and weaker mastery deltas.
- Treatment cohort receives evidence-memory-style resource consumption, higher review completion, and stronger KC mastery deltas.
- Write logs into real tables: `classrooms`, `classroom_members`, `kc_mastery`, `kc_mastery_event`, `kc_review_state`, `learning_resource`, and `learning_event_log`.
- Aggregate from those same tables, not from in-memory counters.

## Metrics

- `avg_mastery`: average `kc_mastery.mastery_score` over active class members and target KC set.
- `review_completion_rate`: `learning_event_log.event_type='review_completed'` / `review_scheduled` in the simulation window.
- `resource_usage_rate`: distinct consumed resources / generated resources, based on `learning_resource` and `resource_consumed` events.
- Stability: treatment must beat baseline on every metric and exceed explicit thresholds.

## Acceptance

- Treatment wins all three metrics.
- Minimum deltas: mastery +0.12, review completion +0.25, resource usage +0.20.
- The test also verifies raw class logs exist, so the result is auditable rather than a synthetic counter.
- A report records the simulated effect numbers and verification command.
