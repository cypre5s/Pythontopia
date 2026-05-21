-- Purpose: persist learner evaluations and their numeric metrics for the review hall.
-- Tables affected: learner_evaluation, learner_evaluation_metric.
-- Irreversible risk: dropping learner_evaluation loses every review-hall snapshot; metric
--                    rows depend on parent evaluations via ON DELETE CASCADE.

CREATE TABLE IF NOT EXISTS learner_evaluation (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  pack_id BIGINT NOT NULL DEFAULT 43 REFERENCES course_pack(id) ON DELETE RESTRICT CHECK (pack_id = 43),
  period_start TIMESTAMPTZ NOT NULL,
  period_end TIMESTAMPTZ NOT NULL,
  narrative_summary TEXT NOT NULL DEFAULT '',
  path_diff_summary TEXT NOT NULL DEFAULT '',
  draft_path_id BIGINT REFERENCES learning_path(id) ON DELETE SET NULL,
  active_path_id BIGINT REFERENCES learning_path(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  acted_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (acted_status IN ('PENDING', 'ACCEPTED', 'KEPT_OLD', 'PARTIAL'))
);

CREATE INDEX IF NOT EXISTS idx_learner_evaluation_user_created
  ON learner_evaluation(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS learner_evaluation_metric (
  id BIGSERIAL PRIMARY KEY,
  evaluation_id BIGINT NOT NULL REFERENCES learner_evaluation(id) ON DELETE CASCADE,
  metric_key VARCHAR(64) NOT NULL,
  numerator NUMERIC(10, 4) NOT NULL DEFAULT 0,
  denominator NUMERIC(10, 4) NOT NULL DEFAULT 0,
  value NUMERIC(10, 4) NOT NULL DEFAULT 0,
  display_unit VARCHAR(16) NOT NULL DEFAULT '',
  notes VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_learner_evaluation_metric_eval
  ON learner_evaluation_metric(evaluation_id);
