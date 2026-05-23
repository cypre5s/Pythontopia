-- Purpose: introduce the pyodide_execution audit table so every browser-side Pyodide preview
--          run can be counted against the per-minute / per-day budget defined in
--          docs/gameplay/r12-deepen/08-pyodide-budget.md §2, and so teacher dashboards (§10)
--          have a single source of truth for status / duration / error categorisation.
-- Tables affected: pyodide_execution (new).
-- Irreversible risk: dropping pyodide_execution loses preview audit history; row growth must be
--                    capped by the 90-day rolling archive policy in §14 before Beta+1.

CREATE TABLE IF NOT EXISTS pyodide_execution (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  duration_ms INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL,
  error_type VARCHAR(64),
  CONSTRAINT pyodide_execution_status_chk CHECK (
    status IN ('OK', 'TIMEOUT', 'OOM', 'WORKER_CRASH', 'BLOCKED', 'TRUNCATED', 'CANCELLED')
  ),
  CONSTRAINT pyodide_execution_duration_chk CHECK (duration_ms >= 0)
);

CREATE INDEX IF NOT EXISTS idx_pyodide_execution_user_started
  ON pyodide_execution (user_id, started_at DESC);

COMMENT ON TABLE  pyodide_execution            IS 'W4 Pyodide 预览执行审计：每次浏览器端 Pyodide 运行写一行，配额、教师 dashboard、画像被动反馈共享同一真相';
COMMENT ON COLUMN pyodide_execution.user_id    IS '执行者，用户被删时审计行级联删除';
COMMENT ON COLUMN pyodide_execution.started_at IS '执行起始时刻，索引按 DESC 排列以便最近窗口查询';
COMMENT ON COLUMN pyodide_execution.duration_ms IS '本次执行耗时（毫秒），BLOCKED / 静态扫描拒绝写 0';
COMMENT ON COLUMN pyodide_execution.status     IS '状态机：OK / TIMEOUT / OOM / WORKER_CRASH / BLOCKED / TRUNCATED / CANCELLED';
COMMENT ON COLUMN pyodide_execution.error_type IS 'Python 异常类名（ZeroDivisionError）或扫描规则 ID（PB-001）';
