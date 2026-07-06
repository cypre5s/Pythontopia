-- 目的：让「聊天记录」按「会话窗口」分组，而不是把每个问题各算作一条独立问答。
--       新增 conversation_id：同一个聊天窗口（一次打开 / 一次「新对话」）内的多次提问共享同一值。
-- 影响表：tutor_session（仅新增一个可空列 + 一个分组查询索引）。
-- 不可逆风险：无。仅 ADD COLUMN（可空）+ CREATE INDEX；旧数据 conversation_id 为 NULL，
--             前端对 NULL 行回退为「每条独立」展示，不影响既有历史。
ALTER TABLE tutor_session ADD COLUMN conversation_id VARCHAR(64);

CREATE INDEX idx_tutor_session_conversation
    ON tutor_session (user_id, conversation_id, created_at);
