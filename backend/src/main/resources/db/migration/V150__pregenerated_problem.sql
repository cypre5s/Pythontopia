-- Purpose: Endless/Boss 题目预生成 + 「退出复用」 + 判题持久化兜底。
--   * 做当前题时后台预生成「下一题」并落库 (status='READY')；
--   * 学生中途退出后，下次登入按维度命中最近一条 READY 行复用（命中即原子标 'CONSUMED'）；
--   * 判题兜底：内存缓存过期 / 进程重启后，凭 user_id + problem_id 取回 payload 中的完整 testCases
--     继续服务端判题。payload 存完整 BossInternalProblem（含 hidden testCases + referenceSolution），
--     仅后端内部使用，永不序列化返前端。
-- Tables affected: pregenerated_problem (新建)；外键依赖 users(id)。
-- Irreversible risk: 删表会丢失所有「已预生成未消费」题（学生需重新等待 LLM 出题）及判题兜底数据，
--   不影响其它业务表。AI 题 problemId 为确定式 (ai-c{ch}-s{stage}-{kc})、同维度会撞号，故行唯一键用
--   自增 id，problem_id 仅作判题取回的检索列（取最新一条），不可当唯一键。

CREATE TABLE IF NOT EXISTS pregenerated_problem (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  chapter INT NOT NULL,
  kc_code VARCHAR(64),
  kind VARCHAR(32),
  arena_level VARCHAR(16),
  difficulty INT,
  problem_id VARCHAR(256),
  payload JSONB NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'READY' CHECK (status IN ('READY', 'CONSUMED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  consumed_at TIMESTAMPTZ
);

-- 取题复用 / 幂等：按 (user, 维度, status, 最新) 命中最近一条 READY 行。
CREATE INDEX IF NOT EXISTS idx_pregenerated_problem_dims
  ON pregenerated_problem (user_id, chapter, kc_code, kind, arena_level, status, created_at DESC);

-- 判题兜底：内存缓存 miss 时凭 user + problem_id 取回最新 payload。
CREATE INDEX IF NOT EXISTS idx_pregenerated_problem_lookup
  ON pregenerated_problem (user_id, problem_id, created_at DESC);
