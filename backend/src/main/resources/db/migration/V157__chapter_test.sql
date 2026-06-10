-- Purpose: R-15 章末大测试——画像驱动「预生成入库、考试期间零 AI 调用」。
--   questions 列由后端权威持有完整题面+答案+解析+证据页 id（绝不下发答案，§4.2 去答案下发）；
--   generator_meta 落 graph 生成审计（run_id/agent/grounding/safety，符合 AGENTS.md 资源生成审计）。
-- Tables affected (新建)：chapter_test。依赖外键：users(id)。
-- Irreversible risk:
--   * drop 丢失全部已预生成/已考试的章测题面、判分明细与防作弊审计；不影响其它业务表。
--   * questions 含编程题 hidden_test_cases 与各题 answer，仅后端内部使用，绝不序列化返前端（去答案下发）。

CREATE TABLE chapter_test (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  chapter INT NOT NULL CHECK (chapter BETWEEN 1 AND 7),
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN
    ('PENDING','GENERATING','READY','IN_PROGRESS','SUBMITTED','EXPIRED','FAILED')),
  attempt_no INT NOT NULL DEFAULT 1,
  questions JSONB,                  -- 服务端持有完整题面+答案+解析+证据页id（绝不下发答案）
  profile_snapshot JSONB,           -- 生成时的画像摘要（可追溯个性化依据）
  generator_meta JSONB,             -- run_id/agent/grounding/safety 结果（防幻觉审计）
  time_limit_sec INT NOT NULL DEFAULT 1800,
  started_at TIMESTAMPTZ, deadline_at TIMESTAMPTZ, submitted_at TIMESTAMPTZ,
  score INT, detail JSONB,          -- 每题判分明细
  integrity JSONB NOT NULL DEFAULT '{}',  -- {paste_blocked:N, blur_events:N, overtime:bool}
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chapter_test_user_ch ON chapter_test(user_id, chapter, created_at DESC);
