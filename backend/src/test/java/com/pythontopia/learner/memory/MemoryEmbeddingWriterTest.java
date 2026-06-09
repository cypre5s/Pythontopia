package com.pythontopia.learner.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MemoryEmbeddingWriter 旁路写入单测：512 维才写，空向量/维度不符/写库异常都不影响主链路。 */
class MemoryEmbeddingWriterTest {

  private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
  private final MemoryEmbeddingRepository repository = mock(MemoryEmbeddingRepository.class);
  private final MemoryEmbeddingWriter writer = new MemoryEmbeddingWriter(embeddingClient, repository);

  @Test
  void storesVectorWhenEmbeddingHasExpectedDimension() {
    float[] vector = new float[MemoryEmbeddingWriter.MEMORY_EMBEDDING_DIM];
    vector[0] = 1.0f;
    when(embeddingClient.embed(anyString())).thenReturn(Optional.of(vector));

    writer.embedAndStore(42L, "practice_failed", Map.of("kcCode", "PY43-KC-016"));

    verify(repository).upsert(eq(42L), eq(vector), eq(MemoryEmbeddingWriter.MEMORY_EMBEDDING_MODEL));
  }

  @Test
  void emptyEmbeddingSkipsRepository() {
    when(embeddingClient.embed(anyString())).thenReturn(Optional.empty());

    writer.embedAndStore(42L, "practice_failed", Map.of());

    verifyNoInteractions(repository);
  }

  @Test
  void dimensionMismatchSkipsRepository() {
    when(embeddingClient.embed(anyString())).thenReturn(Optional.of(new float[] {1.0f, 0.0f}));

    writer.embedAndStore(42L, "practice_failed", Map.of());

    verifyNoInteractions(repository);
  }

  @Test
  void repositoryFailureIsSwallowed() {
    float[] vector = new float[MemoryEmbeddingWriter.MEMORY_EMBEDDING_DIM];
    when(embeddingClient.embed(anyString())).thenReturn(Optional.of(vector));
    doThrow(new RuntimeException("db down")).when(repository).upsert(anyLong(), any(), anyString());

    writer.embedAndStore(42L, "practice_failed", Map.of());
  }
}
