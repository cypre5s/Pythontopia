-- Purpose: B3 — 前端 LearningEvent 落库，支持跨设备进度恢复。
-- Tables affected: learning_event_log（新建）
-- Irreversible risk: 删表会丢失所有前端学习事件日志。

CREATE TABLE IF NOT EXISTS learning_event_log (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  event_type VARCHAR(64) NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}',
  source VARCHAR(32) NOT NULL DEFAULT 'frontend',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_learning_event_log_user_type
  ON learning_event_log(user_id, event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_learning_event_log_created
  ON learning_event_log(created_at DESC);

COMMENT ON TABLE learning_event_log
  IS 'B3：前端学习事件落库（dialog_complete / resource_consumed / submission / kc_mastery / building_visit），跨设备进度恢复';
COMMENT ON COLUMN learning_event_log.event_type
  IS '事件类型：dialog_complete / resource_consumed / submission_passed / submission_failed / kc_mastery_reached / building_visited';
COMMENT ON COLUMN learning_event_log.payload
  IS '事件载荷 JSONB，结构因 event_type 而异（见 LearningEventLogDto）';
COMMENT ON COLUMN learning_event_log.source
  IS '来源标识：frontend / backend / graph';
