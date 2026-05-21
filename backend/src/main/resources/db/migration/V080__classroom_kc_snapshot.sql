-- Purpose: cache classroom KC mastery summary so the teacher dashboard can render quickly.
-- Tables affected: classroom_kc_snapshot.
-- Irreversible risk: dropping the table only invalidates the cache; the dashboard service
--                    recomputes from kc_mastery / kc_mastery_event on demand.

CREATE TABLE IF NOT EXISTS classroom_kc_snapshot (
  id BIGSERIAL PRIMARY KEY,
  classroom_id BIGINT NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
  kc_code VARCHAR(64) NOT NULL,
  avg_mastery NUMERIC(4, 3) NOT NULL DEFAULT 0,
  student_count INT NOT NULL DEFAULT 0,
  passed_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  top_error_categories JSONB NOT NULL DEFAULT '[]'::jsonb,
  computed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (classroom_id, kc_code)
);

CREATE INDEX IF NOT EXISTS idx_classroom_kc_snapshot_classroom
  ON classroom_kc_snapshot(classroom_id);
