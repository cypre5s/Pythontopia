package com.pythontopia.npc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.memory.MemoryEmbeddingRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NpcMemoryServiceTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final MemoryEmbeddingRepository embeddings = mock(MemoryEmbeddingRepository.class);
  private final NpcAffinityService affinity = mock(NpcAffinityService.class);
  private final NpcMemoryService service =
      new NpcMemoryService(jdbc, new ObjectMapper(), embeddings, affinity);

  @Test
  void checksExistingVectorsInOneBatchAndUpdatesFirstNearDuplicate() {
    float[] current = {1.0f, 0.0f};
    when(embeddings.find(99L)).thenReturn(Optional.of(current));
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(10L, 20L, 30L));
    when(embeddings.findAll(List.of(10L, 20L, 30L)))
        .thenReturn(
            Map.of(
                10L, new float[] {0.0f, 1.0f},
                20L, new float[] {0.99f, 0.1f},
                30L, new float[] {1.0f, 0.0f}));

    service.remember(7L, "town_guide", 99L, 0.7);

    verify(embeddings).findAll(List.of(10L, 20L, 30L));
    verify(embeddings, never()).find(10L);
    verify(embeddings, never()).find(20L);
    verify(embeddings, never()).find(30L);
    verify(jdbc).update(
        contains("UPDATE npc_memory"), eq(7L), eq("town_guide"), eq(20L));
    verify(affinity).bump(7L, "town_guide", 0.05);
  }
}
