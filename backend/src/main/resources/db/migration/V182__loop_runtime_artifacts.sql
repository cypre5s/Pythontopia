-- 目的：补齐 Agentic Loop runtime 的可恢复审计骨架：policy、checkpoint、agent run、
-- tool call、eval result 均可绑定到 loop_run，用于恢复、回放和离线评估。
-- 影响表：新增 policy_registry、loop_checkpoint、agent_run、agent_tool_call、loop_eval_result。
-- 不可逆风险：无既有数据改动；回滚可 DROP 新表，但会丢失运行时审计与评估记录。

CREATE TABLE policy_registry (
    id BIGSERIAL PRIMARY KEY,
    namespace TEXT NOT NULL,
    policy_key TEXT NOT NULL,
    policy_version TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT policy_registry_namespace_nonblank CHECK (length(trim(namespace)) > 0),
    CONSTRAINT policy_registry_key_nonblank CHECK (length(trim(policy_key)) > 0),
    CONSTRAINT policy_registry_version_nonblank CHECK (length(trim(policy_version)) > 0),
    CONSTRAINT policy_registry_status_valid CHECK (
        status IN ('DRAFT', 'ACTIVE', 'CANARY', 'RETIRED', 'REJECTED')
    ),
    CONSTRAINT uq_policy_registry_version UNIQUE (namespace, policy_key, policy_version)
);

CREATE INDEX idx_policy_registry_active
    ON policy_registry (namespace, policy_key, status, updated_at DESC);

CREATE TABLE loop_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    loop_run_id BIGINT NOT NULL REFERENCES loop_run(id) ON DELETE CASCADE,
    step_index INT NOT NULL,
    status TEXT NOT NULL,
    state JSONB NOT NULL DEFAULT '{}'::jsonb,
    resume_cursor TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT loop_checkpoint_step_non_negative CHECK (step_index >= 0),
    CONSTRAINT loop_checkpoint_status_nonblank CHECK (length(trim(status)) > 0),
    CONSTRAINT uq_loop_checkpoint_step UNIQUE (loop_run_id, step_index)
);

CREATE INDEX idx_loop_checkpoint_run_order
    ON loop_checkpoint (loop_run_id, step_index);

CREATE TABLE agent_run (
    id BIGSERIAL PRIMARY KEY,
    loop_run_id BIGINT NOT NULL REFERENCES loop_run(id) ON DELETE CASCADE,
    agent_name TEXT NOT NULL,
    status TEXT NOT NULL,
    input_summary TEXT NOT NULL DEFAULT '',
    output_artifact JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_run_name_nonblank CHECK (length(trim(agent_name)) > 0),
    CONSTRAINT agent_run_status_nonblank CHECK (length(trim(status)) > 0)
);

CREATE INDEX idx_agent_run_run_order ON agent_run (loop_run_id, id);

CREATE TABLE agent_tool_call (
    id BIGSERIAL PRIMARY KEY,
    loop_run_id BIGINT NOT NULL REFERENCES loop_run(id) ON DELETE CASCADE,
    checkpoint_id BIGINT REFERENCES loop_checkpoint(id) ON DELETE SET NULL,
    tool_name TEXT NOT NULL,
    request_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL,
    latency_ms INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_tool_call_name_nonblank CHECK (length(trim(tool_name)) > 0),
    CONSTRAINT agent_tool_call_status_nonblank CHECK (length(trim(status)) > 0),
    CONSTRAINT agent_tool_call_latency_non_negative CHECK (latency_ms >= 0)
);

CREATE INDEX idx_agent_tool_call_run_order ON agent_tool_call (loop_run_id, id);
CREATE INDEX idx_agent_tool_call_checkpoint ON agent_tool_call (checkpoint_id);

CREATE TABLE loop_eval_result (
    id BIGSERIAL PRIMARY KEY,
    loop_run_id BIGINT NOT NULL REFERENCES loop_run(id) ON DELETE CASCADE,
    dataset_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    loop_type TEXT NOT NULL,
    policy_version TEXT NOT NULL,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    passed BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT loop_eval_result_dataset_nonblank CHECK (length(trim(dataset_id)) > 0),
    CONSTRAINT loop_eval_result_item_nonblank CHECK (length(trim(item_id)) > 0),
    CONSTRAINT loop_eval_result_type_valid CHECK (
        loop_type IN ('LEARNER', 'DEBUG', 'RESOURCE', 'WORLD', 'IMPROVE')
    ),
    CONSTRAINT loop_eval_result_policy_nonblank CHECK (length(trim(policy_version)) > 0)
);

CREATE INDEX idx_loop_eval_result_run_order ON loop_eval_result (loop_run_id, id);
CREATE INDEX idx_loop_eval_result_dataset ON loop_eval_result (dataset_id, loop_type, created_at DESC);
