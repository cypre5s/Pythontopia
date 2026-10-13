-- Purpose: cap server-side problem-generation requests at 36 per run and three per node/boss phase.
-- Tables affected: expedition_problem_generation_attempt (new audit/budget table).
-- Irreversible risk: dropping the table removes generation-budget history. Legacy node-bound
--   problem rows are conservatively counted, including approved-bank rows whose origin is not
--   recorded; this can exhaust a legacy run's allowance. Earlier failed direct-generation calls
--   that never persisted a problem row cannot be reconstructed.

CREATE TABLE expedition_problem_generation_attempt (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  run_id UUID NOT NULL,
  node_id VARCHAR(32) NOT NULL,
  boss_phase INT CHECK (boss_phase BETWEEN 1 AND 3),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_expedition_problem_generation_node
  ON expedition_problem_generation_attempt (user_id, run_id, node_id, boss_phase, created_at);

-- Preserve the budget already used by legacy run-bound rows. attempt_count represents retries
-- for PENDING/FAILED jobs; cap the backfill at the new node/phase and per-run maxima.
WITH legacy_attempts AS (
  SELECT problem.user_id,
         problem.expedition_run_id AS run_id,
         problem.expedition_node_id AS node_id,
         CASE
           WHEN problem.problem_id ~ '^issue-boss-phase-[1-3]-'
             THEN substring(problem.problem_id FROM '^issue-boss-phase-([1-3])-')::INT
           WHEN map_node.value ->> 'kind' = 'boss'
             THEN CASE
               WHEN map_node.value ->> 'bossPhase' ~ '^[1-3]$'
                 THEN (map_node.value ->> 'bossPhase')::INT
               ELSE 1
             END
           ELSE NULL
         END AS boss_phase,
         problem.created_at,
         problem.id,
         retry.attempt_no
  FROM pregenerated_problem AS problem
  JOIN expedition_run AS run
    ON run.user_id = problem.user_id
   AND run.run_id = problem.expedition_run_id
  LEFT JOIN LATERAL (
    SELECT node.value
    FROM jsonb_array_elements(run.nodes) AS node(value)
    WHERE node.value ->> 'id' = problem.expedition_node_id
    LIMIT 1
  ) AS map_node ON TRUE
  CROSS JOIN LATERAL generate_series(
    1,
    LEAST(GREATEST(problem.attempt_count, 1), 3)
  ) AS retry(attempt_no)
  WHERE problem.expedition_run_id IS NOT NULL
    AND problem.expedition_node_id IS NOT NULL
    AND problem.status IN ('PENDING', 'READY', 'FAILED', 'CONSUMED')
), ranked_legacy_attempts AS (
  SELECT user_id,
         run_id,
         node_id,
         boss_phase,
         created_at,
         id,
         attempt_no,
         row_number() OVER (
           PARTITION BY user_id, run_id, node_id, COALESCE(boss_phase, 0)
           ORDER BY created_at, id, attempt_no
         ) AS node_phase_order
  FROM legacy_attempts
), bounded_legacy_attempts AS (
  SELECT user_id,
         run_id,
         node_id,
         boss_phase,
         row_number() OVER (
           PARTITION BY user_id, run_id
           ORDER BY created_at, id, attempt_no, node_id, COALESCE(boss_phase, 0)
         ) AS run_order
  FROM ranked_legacy_attempts
  WHERE node_phase_order <= 3
)
INSERT INTO expedition_problem_generation_attempt (user_id, run_id, node_id, boss_phase)
SELECT user_id, run_id, node_id, boss_phase
FROM bounded_legacy_attempts
WHERE run_order <= 36;

COMMENT ON TABLE expedition_problem_generation_attempt
  IS 'Server-side DeepSeek problem-generation reservations, capped at 36 per run and three per node/boss phase';
