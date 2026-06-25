-- Purpose: persist selective evidence-memory snapshots derived from the learning evidence graph.
-- Tables affected: learner_evidence_memory_snapshot.
-- Irreversible risk: none; snapshots are derived projections and can be regenerated.

CREATE TABLE IF NOT EXISTS learner_evidence_memory_snapshot (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  focus_kc VARCHAR(128),
  direct_signal_count INTEGER NOT NULL DEFAULT 0,
  propagated_signal_count INTEGER NOT NULL DEFAULT 0,
  retained_evidence_count INTEGER NOT NULL DEFAULT 0,
  graph_memory_score NUMERIC(6,4) NOT NULL DEFAULT 0,
  naive_memory_score NUMERIC(6,4) NOT NULL DEFAULT 0,
  snapshot_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_learner_evidence_memory_snapshot_user_time
  ON learner_evidence_memory_snapshot(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_learner_evidence_memory_snapshot_focus
  ON learner_evidence_memory_snapshot(user_id, focus_kc, created_at DESC);
