-- Purpose: seed course_kc.prerequisite_codes with a teaching-order prerequisite chain so the
--   PathPlanner can do真实的 DAG 拓扑排序（此前该列存在但全为空，planner 拿不到先修关系）。
-- Tables affected: course_kc (UPDATE prerequisite_codes only; no row add/remove → 65 KC 验收不变).
-- Irreversible risk: low; only fills an empty array column. Re-running is guarded by the empty check.
--
-- 先修模型：按 kc_code 教学顺序（PY43-KC-001..065，零填充故字典序==数字序）取前一个 KC 作为先修。
-- 这是课程实际授课顺序派生出的最小有效 DAG（无语义先修元数据时的合理选择），
-- 让 planner 能在「目标 KC 的先修未掌握」时优先安排先修。
-- 注意：必须按 kc_code 排序而非 id —— course_kc.id 与 kc_code 教学顺序不一致，
-- 按 id 排会得到语义错乱（甚至看似 KC-001 先修 KC-032）的先修链。

WITH ordered AS (
  SELECT id,
         kc_code,
         LAG(kc_code) OVER (PARTITION BY pack_id ORDER BY kc_code) AS prev_code
  FROM course_kc
)
UPDATE course_kc c
SET prerequisite_codes = ARRAY[o.prev_code]
FROM ordered o
WHERE c.id = o.id
  AND o.prev_code IS NOT NULL
  AND (c.prerequisite_codes IS NULL OR cardinality(c.prerequisite_codes) = 0);
