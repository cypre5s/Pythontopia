-- Purpose: 费曼判题从 KC 绑定到代表题，才能复用题目 hidden_test_cases 做真实输出比对。
-- Tables affected: feynman_session（新增 problem_id FK + 回填）。
-- Irreversible risk: 删除该列会丢失历史费曼会话对应的代表题判题证据链。

ALTER TABLE feynman_session
  ADD COLUMN IF NOT EXISTS problem_id BIGINT REFERENCES course_problem(id) ON DELETE SET NULL;

-- 回填：每个会话按 KC 选一道代表题，优先有隐藏用例的题，其次按 id 稳定取最小。
-- 用相关标量子查询（不能用 UPDATE ... FROM LATERAL 引用目标表 fs）；
-- 嵌套 CASE 确保 jsonb_array_length 只在确为 JSON 数组时调用，避免对非数组报错。
UPDATE feynman_session fs
SET problem_id = (
  SELECT cp.id
  FROM course_problem cp
  WHERE cp.pack_id = 43
    AND cp.kc_code = fs.kc_code
  ORDER BY
    CASE
      WHEN jsonb_typeof(cp.hidden_test_cases) = 'array' THEN
        CASE WHEN jsonb_array_length(cp.hidden_test_cases) > 0 THEN 0 ELSE 1 END
      ELSE 1
    END,
    cp.id
  LIMIT 1
)
WHERE fs.problem_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_feynman_session_problem
  ON feynman_session(problem_id);
