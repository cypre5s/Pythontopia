-- Purpose: persist student practice submissions, judge results and debug cases.
-- Tables affected: practice_submission, judge_result, debug_case.
-- Irreversible risk: dropping these tables loses submission audit trail and breaks
--                    the bug mine retry analytics (stage 10).

CREATE TABLE IF NOT EXISTS practice_submission (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  problem_id BIGINT NOT NULL REFERENCES course_problem(id) ON DELETE CASCADE,
  quest_id BIGINT REFERENCES quest(id) ON DELETE SET NULL,
  source VARCHAR(24) NOT NULL DEFAULT 'TRAINING'
    CHECK (source IN ('TRAINING', 'PATH_QUEST', 'BUG_MINE_RETRY', 'CLASSROOM')),
  language VARCHAR(16) NOT NULL DEFAULT 'PYTHON_3_11',
  code TEXT NOT NULL,
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'RUNNING', 'PASSED', 'FAILED', 'ERROR')),
  runtime_ms INT,
  memory_kb INT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_practice_submission_user
  ON practice_submission(user_id, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_practice_submission_problem
  ON practice_submission(problem_id);
CREATE INDEX IF NOT EXISTS idx_practice_submission_quest
  ON practice_submission(quest_id);

CREATE TABLE IF NOT EXISTS judge_result (
  id BIGSERIAL PRIMARY KEY,
  submission_id BIGINT NOT NULL UNIQUE REFERENCES practice_submission(id) ON DELETE CASCADE,
  verdict VARCHAR(16) NOT NULL CHECK (
    verdict IN ('AC', 'WA', 'RE', 'TLE', 'CE', 'MLE', 'SYSTEM_ERROR')
  ),
  passed_cases INT NOT NULL DEFAULT 0,
  total_cases INT NOT NULL DEFAULT 0,
  failure_reason TEXT,
  trace JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_judge_result_verdict
  ON judge_result(verdict);

CREATE TABLE IF NOT EXISTS debug_case (
  id BIGSERIAL PRIMARY KEY,
  submission_id BIGINT NOT NULL REFERENCES practice_submission(id) ON DELETE CASCADE,
  kind VARCHAR(24) NOT NULL CHECK (
    kind IN ('COUNTER_EXAMPLE', 'VARIABLE_TRACE', 'WRONG_ASSUMPTION', 'HINT_LAYER')
  ),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_debug_case_submission
  ON debug_case(submission_id);
