-- 目的：让百晓工坊「历史问答」覆盖所有聊天工具结果，而不只依赖 tutor_session 问答表。
-- 影响表：新增 workshop_chat_turn；不修改 tutor_session，历史查询由应用层 UNION 兼容旧问答记录。
-- 不可逆风险：无业务数据回写；回滚可通过后续迁移停用接口后 DROP 新表。
CREATE TABLE workshop_chat_turn (
    id               BIGSERIAL       PRIMARY KEY,
    user_id          BIGINT          NOT NULL REFERENCES users(id),
    conversation_id  VARCHAR(64)     NOT NULL,
    user_text        TEXT            NOT NULL,
    intent           VARCHAR(32)     NOT NULL,
    response_kind    VARCHAR(32)     NOT NULL,
    tool_label       VARCHAR(64),
    response_text    TEXT,
    response_payload JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workshop_chat_turn_user_created
    ON workshop_chat_turn(user_id, created_at DESC);

CREATE INDEX idx_workshop_chat_turn_conversation
    ON workshop_chat_turn(user_id, conversation_id, created_at);
