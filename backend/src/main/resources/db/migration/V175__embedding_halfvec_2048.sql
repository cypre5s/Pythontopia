-- Purpose: Align persisted semantic vectors with Alethicode/Zhipu embedding-3 (2048 dims).
-- Tables affected: memory_embedding, workshop_upload_chunk.
-- Irreversible risk: existing 512-dim vectors are deleted/nullified and must be rebuilt from
-- learning_event_log / workshop_upload.extracted_text. Business records are preserved.
-- Notes: embedding-3 is 2048 dims; pgvector HNSW for >2000 dims requires halfvec.

DROP INDEX IF EXISTS idx_memory_embedding_cos;
DROP INDEX IF EXISTS idx_workshop_upload_chunk_cos;

DELETE FROM memory_embedding;

UPDATE workshop_upload_chunk
   SET embedding = NULL,
       model = NULL
 WHERE embedding IS NOT NULL;

ALTER TABLE memory_embedding
    ALTER COLUMN embedding TYPE halfvec(2048) USING embedding::halfvec(2048);

ALTER TABLE workshop_upload_chunk
    ALTER COLUMN embedding TYPE halfvec(2048) USING embedding::halfvec(2048);

CREATE INDEX IF NOT EXISTS idx_memory_embedding_cos
    ON memory_embedding USING hnsw (embedding halfvec_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_workshop_upload_chunk_cos
    ON workshop_upload_chunk USING hnsw (embedding halfvec_cosine_ops);
