-- V196: 会话私有、短期导学图片附件。
--
-- 目的：图片以 owner/session 绑定 UUID 引用进入 Tutor workflow；原始字节仅短期保存在
--   tutor_workflow_attachment，运行时由 Graph 在卡片生成阶段按 owner+session 读取。
-- 影响表：新建 tutor_workflow_attachment；会话删除时级联清理。
-- 不可逆风险：过期清理会永久删除图片；图片不是课程证据或用户文件，不提供恢复入口。

CREATE TABLE tutor_workflow_attachment (
    id UUID PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES tutor_workflow_session (id) ON DELETE CASCADE,
    content_type VARCHAR(16) NOT NULL
        CHECK (content_type IN ('image/png', 'image/jpeg', 'image/gif', 'image/webp')),
    content BYTEA NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (octet_length(content) BETWEEN 1 AND 5242880)
);

CREATE INDEX idx_tutor_workflow_attachment_expiry
    ON tutor_workflow_attachment (expires_at);
