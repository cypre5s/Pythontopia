-- Purpose: 文件解析 B 通路·学生个人语料分块向量存储，支撑 scoped 语义检索
--   （仅进带标签 context，绝不当 packId=43 课件 evidence）。
-- Tables affected: 新建 workshop_upload_chunk；依赖 V162 的 workshop_upload，复用 V141 已启用的 vector 扩展。
-- Irreversible risk: 回滚需 DROP TABLE workshop_upload_chunk；分块与向量可由 workshop_upload.extracted_text
--   重新切块向量化，无业务数据损失。
-- 维度固定 512，对齐 BgeSmallEmbedder（pythontopia-rag /embed）。embedding 可空：向量化 fail-soft 时
--   chunk 仍入库、embedding 留空，检索回退字面匹配（ILIKE）。
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS workshop_upload_chunk (
  id          BIGSERIAL PRIMARY KEY,
  upload_id   BIGINT NOT NULL REFERENCES workshop_upload(id) ON DELETE CASCADE,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  chunk_index INTEGER NOT NULL,
  text        TEXT NOT NULL,
  embedding   vector(512),
  model       VARCHAR(64),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (upload_id, chunk_index)
);

-- 按上传删除/重切块的高频路径。
CREATE INDEX IF NOT EXISTS idx_workshop_upload_chunk_upload
  ON workshop_upload_chunk (upload_id);
-- 检索强制 user_id scoped（只检索本人上传），按用户过滤。
CREATE INDEX IF NOT EXISTS idx_workshop_upload_chunk_user
  ON workshop_upload_chunk (user_id);
-- 余弦近邻检索（1 - 距离 = 相似度），与 memory_embedding 同范式。
CREATE INDEX IF NOT EXISTS idx_workshop_upload_chunk_cos
  ON workshop_upload_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
