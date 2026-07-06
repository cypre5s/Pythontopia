-- Purpose: 复用 chapter_test 支撑章节小测(QUIZ)与章末大测试(FINAL)，并保持两类尝试隔离。
-- Tables affected: chapter_test（新增 assessment_kind 与查询索引）。
-- Irreversible risk: 删除该列会丢失小测/章末类型边界；既有记录统一回填为 FINAL。

ALTER TABLE chapter_test
  ADD COLUMN assessment_kind VARCHAR(16) NOT NULL DEFAULT 'FINAL'
  CHECK (assessment_kind IN ('QUIZ', 'FINAL'));

CREATE INDEX idx_chapter_test_user_ch_kind
  ON chapter_test(user_id, chapter, assessment_kind, created_at DESC);
