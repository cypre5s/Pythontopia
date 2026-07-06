CREATE TABLE tutor_session (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT          NOT NULL REFERENCES users(id),
    question    TEXT            NOT NULL,
    answer_md   TEXT,
    evidence_page_ids INTEGER[],
    grounding_status  VARCHAR(30),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tutor_session_user ON tutor_session(user_id, created_at DESC);
