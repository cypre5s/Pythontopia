-- Purpose: add an optional course-backed objective item for Boss fallback without changing the
--          required question count or completion state of its mainline stage.
-- Tables affected: mainline_practice_question (one boolean column and one supplemental row).
-- Irreversible risk: dropping the column or row removes this Boss fallback item; existing stage
--                     questions and student progress remain unchanged.

ALTER TABLE mainline_practice_question
  ADD COLUMN required_for_stage BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO mainline_practice_question
  (chapter, stage_index, ordinal, kc_code, type, stem, options_json, answer_json,
   blank_count, explanation, source_label, required_for_stage)
VALUES
  (2, 1, 5, 'PY43-KC-008', 'CHOICE',
   '在 IDLE 的交互式环境中，输入一行 Python 代码后，通常怎样执行？',
   '["按回车后立即执行", "先保存为 .py 文件再运行", "必须点击 Run Module", "重启解释器后运行"]'::jsonb,
   '["按回车后立即执行"]'::jsonb,
   0,
   '课件第 30 页说明，交互式环境采用“一问一答”方式，输入代码后按回车即运行。',
   '课件页 10756',
   FALSE);
