-- Purpose: 异步媒体合成作业（故事板分镜 → 短视频合成）持久化。把"重、慢、受讯飞路数限制"的
--   合成从 HTTP 请求线程里解耦成可排队/可去重/可上报进度的后台作业，支撑高并发提交。
-- Tables affected: media_job (new)。asset_id 引用 media_asset(id)（V124）存放最终成片。
-- Irreversible risk: 删表会丢失作业历史与进度；成片本身在 media_asset（文件可重生、不入 git），
--   重新提交作业即可恢复，故损失可逆。

CREATE TABLE IF NOT EXISTS media_job (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  -- 作业类型：当前仅故事板短视频合成；预留扩展（如未来音频导出/批量出图）。
  kind VARCHAR(32) NOT NULL CHECK (kind IN ('STORYBOARD_VIDEO')),
  -- 生命周期：PENDING 入队 → RUNNING 合成中 → SUCCESS / FAILED。
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')),
  -- spec 指纹（sha-256 hex）：同一分镜规格幂等命中已成片，避免重复合成（高并发护栏）。
  fingerprint VARCHAR(64) NOT NULL,
  -- 提交时的分镜规格快照（scenes：image_prompt / narration / voice / duration / kc_codes）。
  spec_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  -- 实时进度（worker 逐镜回写，供 SSE / 轮询）：
  --   {phase, sceneIndex, sceneTotal, roleKey, message, percent}
  progress_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  -- 成片媒资：合成成功后指向 media_asset.id（失败 / 进行中为空）。
  asset_id BIGINT REFERENCES media_asset(id) ON DELETE SET NULL,
  -- 失败码（如 VIDEO_COMPOSE_UNAVAILABLE / VIDEO_COMPOSE_FAILED），成功为空。
  error_code VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_media_job_user_created
  ON media_job(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_media_job_status
  ON media_job(status);
-- 幂等命中查询：按 user + fingerprint 找已成功成片，命中则直接复用，不再重复合成。
CREATE INDEX IF NOT EXISTS idx_media_job_fingerprint
  ON media_job(user_id, fingerprint);
