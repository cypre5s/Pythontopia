package com.pythontopia.workshop.upload.retrieval;

import com.pythontopia.learner.memory.EmbeddingClient;
import com.pythontopia.workshop.upload.retrieval.dto.RetrievalHit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人语料语义检索：把查询向量化后做余弦近邻检索；向量不可用（RAG 离线）或余弦无命中时回退字面匹配（ILIKE）。
 *
 * <p>始终按登录态 {@code userId} 强制 scoped（只检索本人上传），{@code uploadIds} 为可选附加过滤。
 * 红线：命中仅供注入<strong>带标签 context</strong>，绝不当课件 evidence。
 */
@Service
public class UploadRetrievalService {

  private static final int DEFAULT_TOP_K = 5;
  private static final int MIN_TOP_K = 1;
  private static final int MAX_TOP_K = 20;

  /** 查询向量期望维度；不符则跳过余弦、走字面回退（fail-soft，避免 pgvector 维度不匹配报错）。 */
  private static final int EMBEDDING_DIM = 2048;

  private final EmbeddingClient embeddingClient;
  private final UploadChunkRepository repository;

  public UploadRetrievalService(
      EmbeddingClient embeddingClient, UploadChunkRepository repository) {
    this.embeddingClient = embeddingClient;
    this.repository = repository;
  }

  /**
   * 取该生 topK 命中。
   *
   * @param query 检索语句；空白 → 空结果。
   * @param uploadIds 可空 / 空：不限上传；非空：仅在这些上传内检索。
   * @param topK 期望条数；clamp 到 {@value #MIN_TOP_K}..{@value #MAX_TOP_K}，null 取默认 {@value
   *     #DEFAULT_TOP_K}。
   */
  @Transactional(readOnly = true)
  public List<RetrievalHit> retrieve(long userId, String query, List<Long> uploadIds, Integer topK) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    int limit = clampTopK(topK);
    List<Long> scopeUploadIds = uploadIds == null ? List.of() : uploadIds;
    Optional<float[]> queryVector = embeddingClient.embed(query);
    if (queryVector.isPresent() && queryVector.get().length == EMBEDDING_DIM) {
      List<RetrievalHit> semantic =
          repository.searchByCosine(userId, queryVector.get(), scopeUploadIds, limit);
      if (!semantic.isEmpty()) {
        return semantic;
      }
    }
    // 向量不可用 / 余弦无命中 → 字面回退（embedding 缺失时仍可命中本人语料）。
    return repository.searchByLiteral(userId, query, scopeUploadIds, limit);
  }

  private static int clampTopK(Integer topK) {
    if (topK == null) {
      return DEFAULT_TOP_K;
    }
    return Math.min(Math.max(topK, MIN_TOP_K), MAX_TOP_K);
  }
}
