-- Purpose: 记忆流语义向量存储，支撑 NPC 记忆与费曼误解的语义相关性检索。
-- Tables affected: 启用 vector 扩展；新建 memory_embedding。
-- Irreversible risk: 回滚需 DROP TABLE memory_embedding（+ 可选 DROP EXTENSION vector）；
--                    向量丢失可由 learning_event_log 事件重算，无业务数据损失。
-- 维度固定 512，对齐 BgeSmallEmbedder（PYT_RAG_EMBEDDER=bge）；hash(1024)/DryRun 不写向量，
-- 检索回退 KC 字面匹配（见 MemoryScorer 五参重载）。
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS memory_embedding (
  event_id   BIGINT PRIMARY KEY REFERENCES learning_event_log(id) ON DELETE CASCADE,
  embedding  vector(512) NOT NULL,
  model      VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_memory_embedding_cos
  ON memory_embedding USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
