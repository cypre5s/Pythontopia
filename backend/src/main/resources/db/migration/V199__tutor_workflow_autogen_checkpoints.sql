-- 目的：持久化固定顺序 AutoGen Tutor 的原生团队状态及每个已完成阶段的业务状态，支持断连后按原请求续跑。
-- 影响表：新增 tutor_workflow_autogen_checkpoint；会话删除时级联清理。
-- 不可逆风险：删除表会丢失未完成 Tutor run 的恢复点；已完成卡片和会话投影不受影响。

CREATE TABLE tutor_workflow_autogen_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES tutor_workflow_session (id) ON DELETE CASCADE,
    run_id UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    stage_index INTEGER NOT NULL DEFAULT -1,
    stage_name VARCHAR(80) NOT NULL DEFAULT '',
    checkpoint JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tutor_workflow_autogen_run UNIQUE (session_id, run_id),
    CONSTRAINT ck_tutor_workflow_autogen_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_tutor_workflow_autogen_stage
        CHECK (stage_index >= -1 AND (stage_index = -1 OR length(trim(stage_name)) > 0)),
    CONSTRAINT ck_tutor_workflow_autogen_checkpoint_object
        CHECK (jsonb_typeof(checkpoint) = 'object'),
    CONSTRAINT ck_tutor_workflow_autogen_status
        CHECK (status IN ('RUNNING', 'INTERRUPTED', 'COMPLETED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_tutor_workflow_autogen_active_session
    ON tutor_workflow_autogen_checkpoint (session_id)
    WHERE status = 'RUNNING';

CREATE INDEX idx_tutor_workflow_autogen_resume
    ON tutor_workflow_autogen_checkpoint
       (session_id, request_fingerprint, status, updated_at DESC, id DESC);
