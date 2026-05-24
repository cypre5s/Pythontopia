-- Purpose: R-13 §5 Phase 2/6 续 — 主线进度 + Boss 副本提交历史落库。
-- Tables affected:
--   1) save_slot（已存在 V108）：新增 GIN 索引 + 列文档，约束 snapshot.mainQuest 字段；
--   2) boss_attempt（新建）：记录玩家每次 Boss 副本提交 + 评测结果，供教师审核 + 自适应难度。
-- Irreversible risk: 删 boss_attempt 会丢失 Boss 模式所有历史提交（教师审核日志 / 自适应难度都失效）。

-- ── 1) save_slot.snapshot 字段约定 ──────────────────────────────────────
-- snapshot JSONB 结构（前端 mainQuest store 与本表保持一致）：
--   {
--     "version": 1,
--     "mainQuest": {
--       "currentChapter": 1..7,                 -- 当前在打的章节
--       "currentSubQuest": 1..5,                -- 章内当前子任务索引
--       "completedSubQuests": ["1-1", "1-2"],   -- 已完成的子任务 ID 列表（去重）
--       "defeatedBosses": [1, 2],               -- 已击败的 boss 章号列表
--       "unlockedMilestones": ["outfit:apprentice"], -- 解锁的 outfit / title / storyboard
--       "recentAiProblems": [                   -- 最近 30 题 AI 出题日志（自适应难度）
--         { "problemId": "ai-c1-s1-...", "kc": "...", "difficulty": 3,
--           "succeededInSec": 42, "timestamp": "2026-05-25T..." }
--       ]
--     }
--   }

-- 索引：让 "查所有在打第 N 章的玩家" / "找已击败 boss=1 的玩家" 走 GIN
-- 反复查询（教师班级面板会查），避免每次扫全表。
CREATE INDEX IF NOT EXISTS idx_save_slot_snapshot_gin
  ON save_slot USING GIN (snapshot jsonb_path_ops);

COMMENT ON INDEX idx_save_slot_snapshot_gin
  IS 'R-13 §5：snapshot.mainQuest 按章节 / boss 状态查询的 GIN 索引（jsonb_path_ops，按等值查最快）';

COMMENT ON COLUMN save_slot.snapshot
  IS 'R-13 §5：玩家完整存档 JSONB；mainQuest 字段结构见 V120 文件头注释，前端 stores/mainQuest.ts 单一源';

-- ── 2) boss_attempt：副本提交历史 ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS boss_attempt (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  problem_id VARCHAR(128) NOT NULL,
  chapter INTEGER NOT NULL
    CONSTRAINT boss_attempt_chapter_range CHECK (chapter BETWEEN 1 AND 7),
  stage INTEGER NOT NULL
    CONSTRAINT boss_attempt_stage_range CHECK (stage BETWEEN 1 AND 4),
  kc_code VARCHAR(32) NOT NULL,
  difficulty INTEGER NOT NULL
    CONSTRAINT boss_attempt_diff_range CHECK (difficulty BETWEEN 1 AND 5),
  -- 提交内容；不存评测时间长的 reference solution，只存玩家代码与统计。
  code TEXT NOT NULL,
  passed BOOLEAN NOT NULL DEFAULT false,
  passed_test_cases INTEGER NOT NULL DEFAULT 0
    CONSTRAINT boss_attempt_passed_nonneg CHECK (passed_test_cases >= 0),
  total_test_cases INTEGER NOT NULL DEFAULT 0
    CONSTRAINT boss_attempt_total_nonneg CHECK (total_test_cases >= 0),
  score INTEGER NOT NULL DEFAULT 0
    CONSTRAINT boss_attempt_score_range CHECK (score BETWEEN 0 AND 100),
  elapsed_sec INTEGER NOT NULL DEFAULT 0
    CONSTRAINT boss_attempt_elapsed_nonneg CHECK (elapsed_sec >= 0),
  failure_first_hint VARCHAR(512),
  runtime_error VARCHAR(512),
  ai_generated BOOLEAN NOT NULL DEFAULT false,
  reviewed BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_boss_attempt_user_chapter
  ON boss_attempt(user_id, chapter, stage);

CREATE INDEX IF NOT EXISTS idx_boss_attempt_kc_passed
  ON boss_attempt(kc_code, passed, created_at DESC);

COMMENT ON TABLE boss_attempt
  IS 'R-13 §5 Phase 6：玩家每次 Boss 副本提交 + 评测；教师审核 / 自适应难度 / 班级 KC 热力图都从此查';
COMMENT ON COLUMN boss_attempt.score
  IS '0-100；全过 ≥ 80（参考 BossService.scoreFor）';
COMMENT ON COLUMN boss_attempt.failure_first_hint
  IS '失败首条 hint（"用例 2 期望 X 实际 Y"），来自 graph evaluator.build_failure_hint';
COMMENT ON COLUMN boss_attempt.runtime_error
  IS '非用例级别错误（forbidden_imports / 解释器缺失等）';
COMMENT ON COLUMN boss_attempt.ai_generated
  IS 'true=AI 出题 / false=golden 兜底（教师重点审 AI 题）';
COMMENT ON COLUMN boss_attempt.reviewed
  IS 'true=5 重审查链通过；教师审核驳回后会被 PracticeAgent 降权该 prompt 模板';
