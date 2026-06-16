-- Purpose: R-14 主线「练」客观题题库（选择/判断/填空）+ 学生答对进度列。
--   每小节固定 4 道客观题（选×2 + 判×1 + 填×1，决策 mix4），按 (章, 小节, 序号) 绑定到 stage。
--   OJ 编程题不在此表——仍走 course_problem + mainline_stage.example_problem_ids + 训练场真实判题。
--   题库 seed 数据由后续迁移 V159 写入（Phase 1 P1：基于视频/PPT 主题的通识题，明确标注非课件原题）。
-- Tables affected:
--   * mainline_practice_question（新建，seed 静态表，全局共享）
--   * mainline_stage_progress（新增列 questions_correct：本人已答对的客观题 id 集合）
-- Irreversible risk:
--   * drop mainline_practice_question 丢题库（seed 表，重灌需重跑 V159）。
--   * drop questions_correct 列会丢「练-客观题」进度（不影响视频/课件/OJ 进度）。

CREATE TABLE mainline_practice_question (
  id BIGSERIAL PRIMARY KEY,
  chapter INT NOT NULL CHECK (chapter BETWEEN 1 AND 7),
  stage_index INT NOT NULL,                  -- 章内小节序号 1..N（对齐 mainline_stage.stage_index）
  ordinal INT NOT NULL,                       -- 小节内题序 1..4
  kc_code VARCHAR(32) NOT NULL,               -- 关联 KC（PY43-KC-NNN）
  type VARCHAR(8) NOT NULL CHECK (type IN ('CHOICE','JUDGE','FILL')),
  stem TEXT NOT NULL,                         -- 题干
  options_json JSONB NOT NULL DEFAULT '[]',   -- CHOICE 选项文本数组；JUDGE/FILL 为 []
  answer_json JSONB NOT NULL,                  -- 正确答案：CHOICE=[选项文本]；JUDGE=["正确"|"错误"]；FILL=[每空答案...]
  blank_count INT NOT NULL DEFAULT 0,          -- FILL 空数；CHOICE/JUDGE 为 0
  explanation TEXT NOT NULL DEFAULT '',        -- 讲解（作答后下发）
  source_label VARCHAR(64) NOT NULL DEFAULT '通识题（非课件原题）',
  UNIQUE (chapter, stage_index, ordinal)
);

CREATE INDEX idx_practice_question_stage
  ON mainline_practice_question (chapter, stage_index, ordinal);

-- 「练-客观题」进度：本人已答对的题 id 集合（与 pages_read / examples_done 同构，并集去重幂等）。
ALTER TABLE mainline_stage_progress
  ADD COLUMN questions_correct BIGINT[] NOT NULL DEFAULT '{}';
