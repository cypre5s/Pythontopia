package com.pythontopia.learner.memory;

import java.util.List;
import java.util.Optional;

/**
 * 记忆向量持久化与余弦检索（pgvector 支撑，维度 512 对齐 BgeSmallEmbedder）。
 *
 * <p>无向量 / 离线场景下记忆检索回退至 KC 字面匹配（见 {@link MemoryScorer}），故本仓储仅在 embedding
 * 可用时被写入与检索。
 */
public interface MemoryEmbeddingRepository {

  /** 写入或更新某事件的记忆向量（按 event_id 幂等 upsert）。 */
  void upsert(long eventId, float[] embedding, String model);

  /** 取某事件的记忆向量；不存在 → {@link Optional#empty()}。 */
  Optional<float[]> find(long eventId);

  /**
   * 按余弦相似度检索该用户记忆向量的 topK（{@code similarity = 1 - 余弦距离}，从高到低）。
   *
   * @param query 查询向量；为空或 limit≤0 → 返回空列表。
   * @return 命中事件及其相似度，按相似度降序；无数据返回空列表。
   */
  List<ScoredEvent> searchByCosine(long userId, float[] query, int limit);

  /** 一条按相似度命中的记忆事件。 */
  record ScoredEvent(long eventId, double similarity) {}
}
