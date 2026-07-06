-- Purpose: 客户端安装包下载次数统计（guide 页「下载 Android App」按钮点击计数）。
-- Tables affected: app_download_stat（新建）。
-- Design: stat_key 主键（如 android_apk），原子自增 hit_count；与业务无外键，匿名可读写（随 /api/downloads/** 放行）。
-- Irreversible risk: 删表仅丢失下载计数统计，不影响任何学习/判题/存档数据。

CREATE TABLE IF NOT EXISTS app_download_stat (
  stat_key VARCHAR(64) PRIMARY KEY,
  hit_count BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 预置 Android 安装包计数行（幂等）。
INSERT INTO app_download_stat (stat_key, hit_count) VALUES ('android_apk', 0)
  ON CONFLICT (stat_key) DO NOTHING;
