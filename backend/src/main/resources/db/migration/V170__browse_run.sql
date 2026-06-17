-- Purpose: 「联网查证」(BROWSE 工具) 自主导学会话与步骤日志持久化。学生发起一个 goal，服务端在
--   安全墙白名单域内有界浏览官方文档/判题站，回带可引用摘要。抓取到的外部 url 是「外部内容，非课件」，
--   仅以带标签 citation 形式呈现，绝不当作 packId=43 课件 evidence/引用。
-- Tables affected: browse_run (new) + browse_step (new)。browse_run.user_id 引用 users(id)，删用户级联
--   清理其全部浏览会话；browse_step 经 run_id 级联清理。
-- Irreversible risk: 删表会丢失学生历史浏览会话的步骤轨迹与命中 url；这些是审计/可问责数据（§8.6），
--   删除后无法恢复，但不影响掌握度等核心学习数据（BROWSE 内容从不进课件证据链）。

CREATE TABLE IF NOT EXISTS browse_run (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- 学生本次查证目标（自然语言）；服务端注入白名单，不接收前端域名。
  goal VARCHAR(500) NOT NULL,
  -- 生命周期：PENDING 受理中 → SUCCESS 正常完成 / TRUNCATED 触 max_steps 截断 / FAILED graph 内 fail-fast。
  -- 同步落库下通常直接是终态；PENDING 保留作前向兼容（未来异步）。
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'SUCCESS', 'TRUNCATED', 'FAILED')),
  -- 全程可读终摘要（不含敏感 DOM）；无摘要时为空串。
  final_summary TEXT NOT NULL DEFAULT '',
  -- 是否因触达有界步数上限被截断（对应 BrowseResult.truncated）。
  truncated BOOLEAN NOT NULL DEFAULT FALSE,
  -- graph 内 fail-fast 原因（status=FAILED 时填，已走步骤仍落 browse_step）；成功为空。
  error VARCHAR(512),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 列表查询：取本人会话按时间倒序。
CREATE INDEX IF NOT EXISTS idx_browse_run_user_created
  ON browse_run(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS browse_step (
  id BIGSERIAL PRIMARY KEY,
  run_id BIGINT NOT NULL REFERENCES browse_run(id) ON DELETE CASCADE,
  -- 步骤序号（0-based，对应 BrowseStep.index），随 run 内有界递增。
  idx INTEGER NOT NULL,
  -- 只读浏览动作；与 BrowseStep.action Literal 对齐。
  action VARCHAR(16) NOT NULL
    CHECK (action IN ('navigate', 'read', 'search', 'stop')),
  -- 该步命中的 url（navigate/read 步有值；search/stop 可空，以空串存）。命中 url 即外部 citation 来源。
  url VARCHAR(1024) NOT NULL DEFAULT '',
  -- 该步可读摘要（已去敏，不含脚本/隐藏域/PII）。
  summary TEXT NOT NULL DEFAULT ''
);

-- 明细查询：按 run 取步骤、序号升序；同时保证 (run_id, idx) 唯一，杜绝重复步骤。
CREATE UNIQUE INDEX IF NOT EXISTS uq_browse_step_run_idx
  ON browse_step(run_id, idx);
