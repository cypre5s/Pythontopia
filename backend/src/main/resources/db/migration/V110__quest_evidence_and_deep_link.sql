-- Purpose: support evidence-gated quest completion and per-quest deep links into the world.
-- Tables affected: quest.
-- Irreversible risk: dropping these columns removes the educational closed loop guard rails;
--                    re-deriving evidence from notes would require manual cleanup of quest_progress.
--
-- 教育闭环：未配置 required_evidence_type 的 quest 保持旧行为（只调 note），
-- 配置后必须随 complete 请求附带匹配的 submission / resource / page_read / kc_mastery_delta，
-- 阻止学生"误点完成"绕过真实练习。

ALTER TABLE quest
  ADD COLUMN IF NOT EXISTS required_evidence_type VARCHAR(32);

ALTER TABLE quest
  ADD CONSTRAINT chk_quest_required_evidence_type
  CHECK (
    required_evidence_type IS NULL
    OR required_evidence_type IN ('submission', 'resource', 'page_read', 'kc_mastery')
  );

ALTER TABLE quest
  ADD COLUMN IF NOT EXISTS required_mastery_delta NUMERIC(5, 2);

ALTER TABLE quest
  ADD COLUMN IF NOT EXISTS target_problem_ids BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[];

ALTER TABLE quest
  ADD COLUMN IF NOT EXISTS target_resource_id BIGINT;

ALTER TABLE quest
  ADD COLUMN IF NOT EXISTS deep_link_route VARCHAR(128);

ALTER TABLE quest
  ADD COLUMN IF NOT EXISTS deep_link_params JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_quest_required_evidence_type
  ON quest(required_evidence_type)
  WHERE required_evidence_type IS NOT NULL;

COMMENT ON COLUMN quest.required_evidence_type IS
  '完成任务需提供的证据类型；NULL 表示无强校验（兼容旧 quest）';
COMMENT ON COLUMN quest.required_mastery_delta IS
  'kc_mastery 证据下需要达到的最低 mastery delta（绝对值，0~1）';
COMMENT ON COLUMN quest.target_problem_ids IS
  'submission 证据下允许的 problem_id 白名单；空数组 = 任意题目';
COMMENT ON COLUMN quest.target_resource_id IS
  'resource 证据下指定的 learning_resource.id；NULL = 任意自有资源';
COMMENT ON COLUMN quest.deep_link_route IS
  '前端"去做"按钮的目标路由，例如 /training、/world、/workshop、/profile-room';
COMMENT ON COLUMN quest.deep_link_params IS
  '前端"去做"按钮附带的 query 参数，例如 {"problemId": 476}';
