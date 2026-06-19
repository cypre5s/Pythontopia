-- Purpose: 无尽远征（Roguelike）run 进度跨会话 DB 持久化（后端权威，替代仅 localStorage）。
--   把一局远征的 HP/气势/得分/学习增益/分支地图/当前节点/历史升级为后端权威，支持跨设备 / 跨会话续局。
--   掌握度 / 达标仍由 learner_mastery 等权威表负责（真判题入库）；本表只存「一局 run 的过程进度」。
--   间隔重复不新建表：复用既有 kc_mastery.last_event_at 作「每 KC 最近练习时间」（见 BossService.selectFrontier）。
-- Tables affected: expedition_run（新建）；外键依赖 users(id)。
-- Design: user_id 作主键 → 每个用户至多一条「活跃 run」（单活跃 run，UPSERT 覆盖；弃局/结束 DELETE）。
--   结构化标量列（phase/focus_chapter/hp/.../score）便于观测与统计；nodes/relics/history 用 JSONB 存
--   前端 RogueNode[] / string[] / RogueHistoryEntry[]，避免节点 schema 漂移时频繁改表。
-- Irreversible risk: 删表或删行会丢失「未完成远征的过程进度」（玩家需重开一局），但掌握度 / 判题结果已在
--   learner_mastery 等权威表持久化、永不回滚，故不影响学情与比赛演示的「测/练」闭环。

CREATE TABLE IF NOT EXISTS expedition_run (
  user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  phase VARCHAR(16) NOT NULL,
  focus_chapter INT,
  hp INT NOT NULL,
  max_hp INT NOT NULL,
  momentum INT NOT NULL DEFAULT 0,
  best_momentum INT NOT NULL DEFAULT 0,
  score INT NOT NULL DEFAULT 0,
  layers INT NOT NULL DEFAULT 0,
  current_node_id VARCHAR(32),
  is_daily BOOLEAN NOT NULL DEFAULT FALSE,
  nodes JSONB NOT NULL DEFAULT '[]'::jsonb,
  relics JSONB NOT NULL DEFAULT '[]'::jsonb,
  history JSONB NOT NULL DEFAULT '[]'::jsonb,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 取活跃 run（GET /api/world/expedition/run）：按 user 直接命中主键，status 兜底过滤。
CREATE INDEX IF NOT EXISTS idx_expedition_run_active
  ON expedition_run (user_id, status);
