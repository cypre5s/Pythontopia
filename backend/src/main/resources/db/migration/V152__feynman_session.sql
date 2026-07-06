-- Purpose: 费曼反演会话——学生教一个镜像其真实错误的 AI 学弟。
-- Tables affected: 新建 feynman_session / feynman_turn。
-- Irreversible risk: drop 丢失教学会话历史；可重新开始会话但历史教学证据不可恢复。
CREATE TABLE IF NOT EXISTS feynman_session (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kc_code VARCHAR(40) NOT NULL,
  tutee_state JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS feynman_turn (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES feynman_session(id) ON DELETE CASCADE,
  role VARCHAR(8) NOT NULL,
  content TEXT NOT NULL,
  taught_fix TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_feynman_session_user
  ON feynman_session(user_id, created_at DESC);
