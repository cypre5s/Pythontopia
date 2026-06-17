-- V171: 有界 Agentic Loop run 审计表（S-spine 第 4 条）。
--
-- 目的：graph S1 在每轮「有界闭环」（understand→plan→dispatch→reflect→replan→compose）结束后
--       回写一条审计记录，承载该轮的 plan 规模（max_steps / steps_used）、命中工具链
--       （tools_used）、critic 闸门结论（grounding_pass / safety_pass）、有界重试计数
--       （replan_count）、token 用量与关联的 Langfuse trace id。支撑 §8.5 可恢复（按轮回放）、
--       §8.6 可问责（全链路可追）与达上界诚实降级的事后核对。
--
-- 影响表：新建 loop_run（外键引用既有 users）；无既有表结构改动。
-- 不可逆风险：无（纯新增；回滚直接 DROP TABLE loop_run，不影响其它模块）。

CREATE TABLE loop_run (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- graph 侧闭环会话标识（orchestrator session）；同 session 多轮以 turn 区分。
    session_id VARCHAR(120) NOT NULL,
    turn INT NOT NULL DEFAULT 0,
    -- 有界上限与实际消耗步数；闭环非自由 ReAct，max_steps 受 [1,8] 约束（镜像 Pydantic le=8）。
    max_steps INT NOT NULL DEFAULT 4,
    steps_used INT NOT NULL DEFAULT 0,
    -- 有界 reflect→replan 触发次数（§0 红线：越界 fail-fast）。
    replan_count INT NOT NULL DEFAULT 0,
    -- 本轮命中的工具链（如 {HANDOUT,BROWSE}）；外部内容工具仅作可审计留痕，不等于课件证据。
    tools_used TEXT[] NOT NULL DEFAULT '{}',
    -- critic 闸门结论：未过且达上界时上层给出诚实「未达证据标准」降级回复，不编造。
    grounding_pass BOOLEAN NOT NULL DEFAULT TRUE,
    safety_pass BOOLEAN NOT NULL DEFAULT TRUE,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    -- 关联的 Langfuse / OTel trace id（观测旁路 fail-soft；缺省空串表示未采集）。
    trace_id VARCHAR(128) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT loop_run_turn_non_negative CHECK (turn >= 0),
    CONSTRAINT loop_run_max_steps_bounds CHECK (max_steps BETWEEN 1 AND 8),
    CONSTRAINT loop_run_steps_used_non_negative CHECK (steps_used >= 0),
    CONSTRAINT loop_run_steps_used_bounded CHECK (steps_used <= max_steps),
    CONSTRAINT loop_run_replan_count_non_negative CHECK (replan_count >= 0),
    CONSTRAINT loop_run_input_tokens_non_negative CHECK (input_tokens >= 0),
    CONSTRAINT loop_run_output_tokens_non_negative CHECK (output_tokens >= 0)
);

-- 按用户拉取其闭环历史（最近轮在前），用于审计/回放。
CREATE INDEX idx_loop_run_user_session ON loop_run (user_id, session_id, turn DESC);

-- 由 Langfuse trace id 反查闭环 run（观测↔审计对账）；仅索引已采集 trace 的行。
CREATE INDEX idx_loop_run_trace ON loop_run (trace_id) WHERE trace_id <> '';
