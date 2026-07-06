-- 目的：M0 trace completeness gate。所有新的 Agentic loop_run 审计必须带可追踪 trace_id。
-- 影响表：loop_run。
-- 不可逆风险：已有历史空 trace 行保留；约束 NOT VALID，避免升级时因历史数据阻断，但新写入会被检查。

ALTER TABLE loop_run
    ADD CONSTRAINT chk_loop_run_trace_id_nonblank
    CHECK (length(trim(trace_id)) > 0) NOT VALID;
