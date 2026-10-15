package com.pythontopia.workshop.upload.retrieval;

import com.pythontopia.learner.memory.EmbeddingClient;
import com.pythontopia.workshop.upload.retrieval.UploadChunkRepository.ChunkRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 「上传就绪 → 分块 + 向量化 + 入库」服务：读 {@code workshop_upload.extracted_text}（按列名经 JdbcTemplate
 * 读，不依赖 parse 通路的实体类），切块，经 {@link EmbeddingClient} 批量取 2048 维向量后整体替换该上传的分块。
 *
 * <p>全程 fail-soft：向量化不可用时 chunk 仍入库、embedding 留空（检索回退字面匹配）；任何异常只记 warn、不抛，
 * 确保<strong>分块旁路失败不影响上传主链路</strong>。Phase 2 由 B-backend-parse 在上传 READY 处调用
 * {@link #indexUploadAsync(long)}（异步、即发即忘）。
 */
@Service
public class UploadCorpusIndexingService {

  /** 向量维度，对齐智谱 embedding-3 / pythontopia-rag /embed。 */
  static final int EMBEDDING_DIM = 2048;

  static final String EMBEDDING_MODEL = "pythontopia-rag/embed";

  private static final Logger log = LoggerFactory.getLogger(UploadCorpusIndexingService.class);

  private final JdbcTemplate jdbc;
  private final UploadChunker chunker;
  private final UploadChunkRepository repository;
  private final EmbeddingClient embeddingClient;

  public UploadCorpusIndexingService(
      JdbcTemplate jdbc,
      UploadChunker chunker,
      UploadChunkRepository repository,
      EmbeddingClient embeddingClient) {
    this.jdbc = jdbc;
    this.chunker = chunker;
    this.repository = repository;
    this.embeddingClient = embeddingClient;
  }

  /** 异步即发即忘入口（Phase 2 集成点）：上传 READY 后调用，不阻塞、不影响上传主链路。 */
  @Async
  public void indexUploadAsync(long uploadId) {
    indexUpload(uploadId);
  }

  /**
   * 同步分块 + 向量化 + 入库；返回写入的分块数（出错 / 无内容返回 0，绝不抛）。
   *
   * <p>幂等：每次都先删旧分块再写新分块，可安全重复触发。
   */
  public int indexUpload(long uploadId) {
    try {
      Optional<UploadSource> source = loadSource(uploadId);
      if (source.isEmpty()) {
        log.warn("upload corpus index skip: upload {} not found", uploadId);
        return 0;
      }
      String extractedText = source.get().extractedText();
      List<String> chunks =
          extractedText == null ? List.of() : chunker.chunk(extractedText);
      if (chunks.isEmpty()) {
        repository.deleteByUploadId(uploadId);
        return 0;
      }
      long userId = source.get().userId();
      List<ChunkRow> rows = embedToRows(uploadId, userId, chunks);
      repository.replaceChunks(uploadId, rows);
      return rows.size();
    } catch (RuntimeException ex) {
      log.warn(
          "upload corpus index failed uploadId={} errorType={}",
          uploadId,
          ex.getClass().getSimpleName());
      return 0;
    }
  }

  /** 批量向量化（fail-soft）后组装分块行；维度不符或缺失的块 embedding 留空。 */
  private List<ChunkRow> embedToRows(long uploadId, long userId, List<String> chunks) {
    List<Optional<float[]>> vectors = embeddingClient.embedBatch(chunks);
    List<ChunkRow> rows = new ArrayList<>(chunks.size());
    for (int i = 0; i < chunks.size(); i++) {
      float[] embedding = vectorAt(vectors, i);
      rows.add(
          new ChunkRow(
              uploadId,
              userId,
              i,
              chunks.get(i),
              embedding,
              embedding == null ? null : EMBEDDING_MODEL));
    }
    return rows;
  }

  private static float[] vectorAt(List<Optional<float[]>> vectors, int index) {
    if (vectors == null || index >= vectors.size()) {
      return null;
    }
    Optional<float[]> vector = vectors.get(index);
    if (vector == null || vector.isEmpty()) {
      return null;
    }
    float[] embedding = vector.get();
    return embedding.length == EMBEDDING_DIM ? embedding : null;
  }

  /** 按列名读取上传源文本（不依赖 parse 通路实体类）；行不存在 → empty。 */
  private Optional<UploadSource> loadSource(long uploadId) {
    List<UploadSource> rows =
        jdbc.query(
            "SELECT user_id, extracted_text FROM workshop_upload WHERE id = ?",
            (rs, rowNum) -> new UploadSource(rs.getLong("user_id"), rs.getString("extracted_text")),
            uploadId);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  private record UploadSource(long userId, String extractedText) {}
}
