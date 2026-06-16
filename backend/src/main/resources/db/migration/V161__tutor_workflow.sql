-- V161: AI 导学工作流投影表（移植 Alethicode tutor workflow，R-16）。
--
-- 目的：训练场「6 阶段导学工作流」（审题→思路→编码→错因→复盘→迁移）的会话与卡片
-- 时间线持久化。graph 服务持运行期阶段状态（进程内单 worker），本表是权威投影：
-- 前端刷新 / graph 进程重启后由此恢复阶段与时间线（请求随附 restore_phase 回放）。
--
-- 影响表：新建 tutor_workflow_session / tutor_workflow_card，无既有表改动。
-- 不可逆风险：无（纯新增；回滚直接 drop 两表，不影响其他模块）。

CREATE TABLE tutor_workflow_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    problem_id BIGINT NOT NULL,
    -- graph 侧会话标识（wf-{userId}-{problemId}-{nonce}），SSE 请求 session_id。
    session_key VARCHAR(80) NOT NULL UNIQUE,
    phase VARCHAR(20) NOT NULL DEFAULT 'READING',
    seq INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 一个学生一道题同时只有一个活跃工作流会话。
CREATE UNIQUE INDEX uq_tutor_workflow_session_active
    ON tutor_workflow_session (user_id, problem_id)
    WHERE status = 'ACTIVE';

CREATE TABLE tutor_workflow_card (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES tutor_workflow_session (id) ON DELETE CASCADE,
    seq INT NOT NULL,
    event VARCHAR(20) NOT NULL,
    card_key VARCHAR(40) NOT NULL,
    -- 整卡 JSON（含 card_id；ai_reply 另带 user_message 供时间线回放学生气泡）。
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tutor_workflow_card_session
    ON tutor_workflow_card (session_id, seq, id);
