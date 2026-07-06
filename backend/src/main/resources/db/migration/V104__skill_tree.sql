-- Purpose: define the global skill tree and per-learner unlocked skills, derived from KC mastery.
-- Tables affected: skill_node, learner_skill.
-- Irreversible risk: dropping learner_skill resets every player's tree progress.

CREATE TABLE IF NOT EXISTS skill_node (
  skill_key VARCHAR(64) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  kc_codes TEXT[] NOT NULL DEFAULT '{}',
  unlock_condition JSONB NOT NULL DEFAULT '{}'::jsonb,
  icon_url VARCHAR(256),
  tier INTEGER NOT NULL DEFAULT 1
    CONSTRAINT skill_node_tier_range CHECK (tier BETWEEN 1 AND 5),
  create_time TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS learner_skill (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  skill_key VARCHAR(64) NOT NULL REFERENCES skill_node(skill_key) ON DELETE RESTRICT,
  unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  proficiency INTEGER NOT NULL DEFAULT 0
    CONSTRAINT learner_skill_proficiency_range CHECK (proficiency BETWEEN 0 AND 100),
  CONSTRAINT uq_learner_skill UNIQUE (user_id, skill_key)
);

CREATE INDEX IF NOT EXISTS idx_learner_skill_user ON learner_skill(user_id);

COMMENT ON TABLE skill_node IS '技能树静态定义，依赖 KC 掌握度解锁';
COMMENT ON TABLE learner_skill IS '玩家已解锁的技能与熟练度';
