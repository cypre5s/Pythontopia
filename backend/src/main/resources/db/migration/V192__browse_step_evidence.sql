-- Purpose: 为联网查证步骤保存结构化网页证据 title/snippet；summary 继续保留，兼容已发布客户端。
-- Tables affected: browse_step (add title, snippet; existing rows use summary backfill snippet)。
-- Irreversible risk: 回滚需删除两列，会丢失页面标题和独立摘录；不影响既有 summary/url。

ALTER TABLE browse_step
  ADD COLUMN IF NOT EXISTS title VARCHAR(512) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS snippet TEXT NOT NULL DEFAULT '';

UPDATE browse_step
SET snippet = summary
WHERE snippet = '' AND summary <> '';
