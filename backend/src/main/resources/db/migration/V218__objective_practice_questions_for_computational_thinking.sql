-- Purpose: provide course-backed objective practice for conceptual KC-003 and KC-004, without
--          adding programming exercises or changing required mainline stage completion.
-- Tables affected: mainline_practice_question (six supplemental rows).
-- Irreversible risk: deleting these rows removes their objective practice options; required
--                     stage questions and existing progress remain unchanged.

INSERT INTO mainline_practice_question
  (chapter, stage_index, ordinal, kc_code, type, stem, options_json, answer_json,
   blank_count, explanation, source_label, required_for_stage)
VALUES
  (1, 1, 100, 'PY43-KC-004', 'JUDGE',
   '2006年，周以真教授发表论文，对计算思维做了明确的定义。',
   '[]'::jsonb, '["正确"]'::jsonb, 0,
   '课件第 41 页说明周以真教授于 2006 年发表论文并对计算思维做出定义。',
   '课件页 10722', FALSE),
  (1, 1, 101, 'PY43-KC-004', 'FILL',
   '计算思维的本质是______和自动化。',
   '[]'::jsonb, '["抽象"]'::jsonb, 1,
   '课件第 42 页明确说明计算思维的本质是抽象和自动化。',
   '课件页 10723', FALSE),
  (1, 1, 102, 'PY43-KC-003', 'CHOICE',
   '计算思维求解问题流程中，“抽象建模”之后的下一步是？',
   '["算法设计", "程序实现", "结果评价", "发现问题"]'::jsonb,
   '["算法设计"]'::jsonb, 0,
   '课件第 43 页给出的流程中，建立模型后用算法精确描述求解过程。',
   '课件页 10724', FALSE),
  (1, 1, 103, 'PY43-KC-003', 'FILL',
   '计算思维的操作流程要求通过抽象建立______模型。',
   '[]'::jsonb, '["数学"]'::jsonb, 1,
   '课件第 43 页说明通过抽象建立数学模型。',
   '课件页 10724', FALSE),
  (1, 1, 104, 'PY43-KC-003', 'JUDGE',
   '计算思维的问题求解流程包括发现问题、抽象建模、算法设计、程序实现和结果评价。',
   '[]'::jsonb, '["正确"]'::jsonb, 0,
   '课件第 43 页列出这五个操作步骤。',
   '课件页 10724', FALSE),
  (1, 1, 105, 'PY43-KC-003', 'CHOICE',
   '课件列出的计算思维特征之一是？',
   '["概念化", "程序化", "只用机器思考", "只适用于程序员"]'::jsonb,
   '["概念化"]'::jsonb, 0,
   '课件第 44 页指出计算思维是概念化而非程序化。',
   '课件页 10725', FALSE);
