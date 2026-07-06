-- Purpose: record finished 无尽远征 (roguelike) runs so a per-class leaderboard can rank
--          students by their best single-run score (真实数据，非前端伪造).
-- Tables affected: expedition_score (new).
-- Irreversible risk: none; this migration only creates a table and indexes.

CREATE TABLE IF NOT EXISTS expedition_score (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  score INTEGER NOT NULL DEFAULT 0 CHECK (score >= 0),
  depth INTEGER NOT NULL DEFAULT 0 CHECK (depth >= 0),
  best_momentum INTEGER NOT NULL DEFAULT 0 CHECK (best_momentum >= 0),
  cleared BOOLEAN NOT NULL DEFAULT FALSE,
  is_daily BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 排行榜按 user 取 MAX(score)，并按分数倒序查询。
CREATE INDEX IF NOT EXISTS idx_expedition_score_user ON expedition_score(user_id);
CREATE INDEX IF NOT EXISTS idx_expedition_score_score ON expedition_score(score DESC);
