-- Purpose: add progressive Bug Mine depth tracking for the errorbook flow.
-- Tables affected: bug_mine_session.
-- Irreversible risk: none; depth_layer is additive and defaults existing sessions to L1.

ALTER TABLE bug_mine_session
  ADD COLUMN IF NOT EXISTS depth_layer INT NOT NULL DEFAULT 1;

ALTER TABLE bug_mine_session
  DROP CONSTRAINT IF EXISTS chk_bug_mine_session_depth_layer;

ALTER TABLE bug_mine_session
  ADD CONSTRAINT chk_bug_mine_session_depth_layer
  CHECK (depth_layer BETWEEN 1 AND 5);

CREATE INDEX IF NOT EXISTS idx_bug_mine_session_user_status_depth
  ON bug_mine_session(user_id, status, depth_layer);
