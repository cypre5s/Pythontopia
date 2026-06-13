package com.pythontopia.workshop.upload.retrieval;

import com.pythontopia.workshop.upload.retrieval.dto.RetrievalHit;
import java.util.List;

/**
 * 个人语料分块的持久化与检索（pgvector 支撑，维度 512 对齐 BgeSmallEmbedder）。
 *
 * <p>所有检索方法都<strong>强制按 {@code userId} scoped</strong>（只命中本人上传），可选再按 {@code uploadIds}
 * 过滤。向量缺失时由 {@link #searchByLiteral} 字面回退（见 {@link UploadRetrievalService}）。
 */
public interface UploadChunkRepository {

  /**
   * 以「先删后插」整体替换某上传的全部分块（支撑重复触发分块时幂等重建）。
   *
   * @param uploadId 目标上传 id；其现有分块先被删除。
   * @param rows 新分块；{@code embedding} 可空（向量化失败时仍入库），空列表表示仅清空。
   */
  void replaceChunks(long uploadId, List<ChunkRow> rows);

  /** 删除某上传的全部分块（上传被删时由 FK CASCADE 兜底，此处用于显式重建/清理）。 */
  void deleteByUploadId(long uploadId);

  /** 某上传当前分块数（测试 / 幂等校验用）。 */
  int countByUploadId(long uploadId);

  /**
   * 按余弦相似度检索该用户分块的 topK（{@code similarity = 1 - 余弦距离}，从高到低）；仅命中有向量的块。
   *
   * @param uploadIds 可空 / 空：不限上传；非空：仅在这些上传内检索。
   * @return 命中（含相似度 score），无数据返回空列表；{@code query} 空或 {@code limit<=0} → 空列表。
   */
  List<RetrievalHit> searchByCosine(long userId, float[] query, List<Long> uploadIds, int limit);

  /**
   * 字面回退检索：{@code text ILIKE %query%}，按 (upload_id, chunk_index) 稳定排序，score 恒为 {@code 0.0}。
   * 用于查询向量不可用 / 余弦无命中时仍能返回相关分块。
   */
  List<RetrievalHit> searchByLiteral(long userId, String query, List<Long> uploadIds, int limit);

  /**
   * 待写入的分块行。
   *
   * @param embedding 512 维向量；{@code null} 表示向量化失败（chunk 仍入库、检索回退字面匹配）。
   * @param model 向量模型标识；{@code embedding} 为空时应为 {@code null}。
   */
  record ChunkRow(
      long uploadId, long userId, int chunkIndex, String text, float[] embedding, String model) {}
}
