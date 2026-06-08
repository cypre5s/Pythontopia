-- Purpose: 记忆流（Grounded Generative Agents）重要度打分，用于 recency×importance×relevance 检索。
-- Tables affected: learning_event_log（加列）
-- Irreversible risk: 回滚需 DROP COLUMN importance（丢失历史重要度，可由 EventImportance 规则重算）。
ALTER TABLE learning_event_log
  ADD COLUMN IF NOT EXISTS importance SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE learning_event_log
  ADD CONSTRAINT learning_event_importance_range CHECK (importance BETWEEN 1 AND 5);
COMMENT ON COLUMN learning_event_log.importance IS '记忆流重要度 1-5（写入时由 EventImportance 规则定，越高越值得反思/检索）';
