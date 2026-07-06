package com.pythontopia.workshop.upload.retrieval.dto;

/**
 * 个人语料检索命中（camelCase JSON）：{@code score} 为余弦相似度（{@code 1 - 距离}，越大越相关）；当向量缺失
 * 走字面匹配（ILIKE）回退时 {@code score} 为 {@code 0.0}（哨兵：匹配来自字面而非语义）。
 *
 * <p>红线：这是 user_id scoped 的学生私有语料，只能注入<strong>带标签 context</strong>，绝不可当课件 evidence。
 */
public record RetrievalHit(
    long uploadId, String filename, int chunkIndex, String text, double score) {}
