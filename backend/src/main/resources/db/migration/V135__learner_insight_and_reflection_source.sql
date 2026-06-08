-- Purpose: 反思洞察落库 + 允许 profile_revision.source='REFLECTION'。
-- Tables affected: learner_insight（新建）、profile_revision（放宽 source check）
-- Irreversible risk: drop learner_insight 丢历史洞察；source check 回退需移除 REFLECTION。
CREATE TABLE IF NOT EXISTS learner_insight (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text VARCHAR(300) NOT NULL,
  dimension_key VARCHAR(40) NOT NULL,
  tags JSONB NOT NULL DEFAULT '[]',
  evidence_event_ids BIGINT[] NOT NULL DEFAULT '{}',
  confidence DOUBLE PRECISION NOT NULL DEFAULT 0.5,
  source VARCHAR(16) NOT NULL DEFAULT 'GRAPH',  -- GRAPH | RULE（兜底）
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_learner_insight_user ON learner_insight(user_id, created_at DESC);

COMMENT ON TABLE learner_insight
  IS '反思洞察（Grounded Generative Agents）：LLM/规则综合记忆流得出的学情洞察，每条强制引用 evidence_event_ids（learning_event_log.id）。';
COMMENT ON COLUMN learner_insight.evidence_event_ids
  IS '证据 event id 数组（learning_event_log.id），防幻觉硬约束：洞察必须可溯源。';
COMMENT ON COLUMN learner_insight.source IS 'GRAPH=ReflectionAgent 综合；RULE=离线/无 key 规则兜底。';

-- 放宽 profile_revision.source 枚举，允许反思回写（对齐 V091 既有枚举 + 新增 REFLECTION）。
ALTER TABLE profile_revision DROP CONSTRAINT IF EXISTS profile_revision_source_check;
ALTER TABLE profile_revision ADD CONSTRAINT profile_revision_source_check
  CHECK (source IN ('INTERVIEW','SUBMISSION','DEBUG','RESOURCE_FEEDBACK','QUEST',
                    'CLASSROOM','EVALUATION','PRACTICE_PASS','PRACTICE_FAIL','DEBUG_FIX','REFLECTION'));
