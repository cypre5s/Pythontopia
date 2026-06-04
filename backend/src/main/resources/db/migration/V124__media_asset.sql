-- Purpose: persist multimodal media assets (AI 文生图 / TTS 音频 / 合成短视频) with degradation audit.
-- Tables affected: media_asset (new).
-- Irreversible risk: dropping this table loses generated media metadata; the underlying files in
--   the generated-media dir are regenerable and not stored in git, so loss is recoverable.

CREATE TABLE IF NOT EXISTS media_asset (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  kind VARCHAR(16) NOT NULL CHECK (kind IN ('IMAGE', 'AUDIO', 'VIDEO')),
  prompt TEXT,
  file_path VARCHAR(512) NOT NULL,
  mime_type VARCHAR(64) NOT NULL,
  -- degraded=true 表示未配置讯飞 apikey（或上游失败）时返回的本地占位资产，
  -- 用于演示验收链路不被阻塞；配置真实密钥后生成的资产 degraded=false。
  degraded BOOLEAN NOT NULL DEFAULT false,
  -- 资产来源：XFYUN（讯飞真实生成）/ PLACEHOLDER（本地占位）/ FFMPEG（程序化合成短视频）。
  source VARCHAR(16) NOT NULL DEFAULT 'XFYUN'
    CHECK (source IN ('XFYUN', 'PLACEHOLDER', 'FFMPEG')),
  kc_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  meta JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_media_asset_user_kind
  ON media_asset(user_id, kind);
CREATE INDEX IF NOT EXISTS idx_media_asset_user_created
  ON media_asset(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_media_asset_kc
  ON media_asset USING GIN (kc_codes);
