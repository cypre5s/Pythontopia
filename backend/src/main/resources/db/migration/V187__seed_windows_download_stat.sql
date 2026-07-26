-- Purpose: 补齐 guide 页真实使用、但 V174 漏 seed 的下载计数行 windows_zip。
--   下载计数端点改为「只 UPDATE 既有行、命中 0 行回 404」（不再 INSERT ... ON CONFLICT 让任意
--   匿名 key 凭空建行），因此合法 key 必须由迁移显式 seed。frontend/src/content/guideSections.ts
--   目前用到两个 key：android_apk（V174 已 seed）与 windows_zip（此处补齐）。
-- Tables affected: app_download_stat（INSERT 1 行，幂等）。
-- Irreversible risk: 无（纯增量 seed，ON CONFLICT DO NOTHING，不覆盖已有计数）。

INSERT INTO app_download_stat (stat_key, hit_count) VALUES ('windows_zip', 0)
  ON CONFLICT (stat_key) DO NOTHING;
