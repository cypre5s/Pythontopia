-- Purpose: FSRS-lite 间隔复习状态（每 user×KC 一行），驱动"遗忘回声"按记忆稳定度个性化排程，
--          替换原先对所有 KC 一刀切的 14 天半衰期。引擎确定性、可单测；不在前端计算。
-- Tables affected: kc_review_state（新建）。
-- Irreversible risk: 删表会丢失复习稳定度/排程；掌握度本身仍存于 kc_mastery / kc_mastery_event，不受影响。

CREATE TABLE IF NOT EXISTS kc_review_state (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kc_code VARCHAR(64) NOT NULL,
  -- 记忆稳定度 S：可提取性 R 从 1.0 衰减到锚点 0.9 所需的天数（FSRS 定义）。
  stability NUMERIC(9, 3) NOT NULL DEFAULT 1.000,
  -- FSRS 卡片难度代理（0..1），仅用于稳定度增量微调，向基线均值回归避免"难度地狱"。
  difficulty NUMERIC(4, 3) NOT NULL DEFAULT 0.300,
  -- 成功召回次数 / 失败（lapse）次数。
  reps INTEGER NOT NULL DEFAULT 0,
  lapses INTEGER NOT NULL DEFAULT 0,
  -- 最近一次"复习"（成功/失败/重学）时间；due_at 为可提取性跌破目标留存率的时刻。
  last_review_at TIMESTAMPTZ,
  due_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT kc_review_state_stability_pos CHECK (stability > 0),
  CONSTRAINT kc_review_state_difficulty_range CHECK (difficulty >= 0.0 AND difficulty <= 1.0),
  CONSTRAINT kc_review_state_reps_nonneg CHECK (reps >= 0 AND lapses >= 0),
  UNIQUE (user_id, kc_code)
);

CREATE INDEX IF NOT EXISTS idx_kc_review_state_user ON kc_review_state(user_id);
CREATE INDEX IF NOT EXISTS idx_kc_review_state_due ON kc_review_state(user_id, due_at);

-- 回填：从既有掌握度快照播种初始稳定度与到期时间，避免上线后历史 KC 全部"立即到期"。
-- 播种公式 S0 = clamp(1 + 13 * mastery_score, 0.5, 14)，承接旧的 14 天半衰期口径
-- （mastery 0 -> 1 天；0.45 -> ~6.85 天；1.0 -> 14 天），due_at = last_event_at + S0 天。
INSERT INTO kc_review_state (
  user_id, kc_code, stability, difficulty, reps, lapses, last_review_at, due_at, created_at, updated_at
)
SELECT
  m.user_id,
  m.kc_code,
  GREATEST(0.5, LEAST(14.0, 1.0 + 13.0 * COALESCE(m.mastery_score, 0)))::numeric(9, 3) AS stability,
  LEAST(1.0, GREATEST(0.0, 1.0 - COALESCE(m.mastery_score, 0)))::numeric(4, 3) AS difficulty,
  0,
  0,
  m.last_event_at,
  CASE
    WHEN m.last_event_at IS NULL THEN NULL
    ELSE m.last_event_at
         + (GREATEST(0.5, LEAST(14.0, 1.0 + 13.0 * COALESCE(m.mastery_score, 0))) || ' days')::interval
  END,
  now(),
  now()
FROM kc_mastery m
ON CONFLICT (user_id, kc_code) DO NOTHING;
