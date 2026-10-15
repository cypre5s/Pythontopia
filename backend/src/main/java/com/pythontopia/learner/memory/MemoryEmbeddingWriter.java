package com.pythontopia.learner.memory;

import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 记忆流向量写入旁路：事件已落 {@code learning_event_log} 后，异步向 RAG 服务取 2048 维 embedding 并写入
 * {@code memory_embedding}。
 *
 * <p>这是旁路增强，不参与学习事件主事务；任何 embedding / 写库失败都只记 warn，不向调用方传播。
 */
@Service
public class MemoryEmbeddingWriter {

  static final int MEMORY_EMBEDDING_DIM = 2048;
  static final String MEMORY_EMBEDDING_MODEL = "pythontopia-rag/embed";

  private static final Logger log = LoggerFactory.getLogger(MemoryEmbeddingWriter.class);

  private final EmbeddingClient embeddingClient;
  private final MemoryEmbeddingRepository memoryEmbeddingRepository;

  public MemoryEmbeddingWriter(
      EmbeddingClient embeddingClient, MemoryEmbeddingRepository memoryEmbeddingRepository) {
    this.embeddingClient = embeddingClient;
    this.memoryEmbeddingRepository = memoryEmbeddingRepository;
  }

  @Async
  public void embedAndStore(long eventId, String eventType, Map<String, Object> payload) {
    try {
      Optional<float[]> vector = embeddingClient.embed(toMemoryText(eventType, payload));
      if (vector.isEmpty()) {
        return;
      }
      float[] embedding = vector.get();
      if (embedding.length != MEMORY_EMBEDDING_DIM) {
        log.warn(
            "memory embedding skip eventId={} due to dim mismatch: expected={} actual={}",
            eventId,
            MEMORY_EMBEDDING_DIM,
            embedding.length);
        return;
      }
      memoryEmbeddingRepository.upsert(eventId, embedding, MEMORY_EMBEDDING_MODEL);
    } catch (RuntimeException ex) {
      log.warn(
          "memory embedding skip eventId={} errorType={}", eventId, ex.getClass().getSimpleName());
    }
  }

  private static String toMemoryText(String eventType, Map<String, Object> payload) {
    return eventType + " " + (payload == null ? "{}" : payload.toString());
  }
}
