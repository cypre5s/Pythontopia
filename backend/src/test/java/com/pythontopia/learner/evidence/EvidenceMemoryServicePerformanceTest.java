package com.pythontopia.learner.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.verifyNoInteractions;

class EvidenceMemoryServicePerformanceTest {

  @Test
  void persistsSnapshotWithoutRebuildingEvidenceGraph() {
    LearningEvidenceGraphService graphService = mock(LearningEvidenceGraphService.class);
    EvidenceMemoryRepository repository = mock(EvidenceMemoryRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    LearningEvidenceGraphDto graph =
        new LearningEvidenceGraphDto(
            7L,
            null,
            List.of(),
            List.of(),
            new EvidenceGraphMetricsDto(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of());
    when(graphService.buildForUser(eq(7L), isNull(), eq(120))).thenReturn(graph);
    when(repository.insert(org.mockito.ArgumentMatchers.any())).thenReturn(42L);
    EvidenceMemoryService service = new EvidenceMemoryService(graphService, repository, jdbc);

    EvidenceMemorySnapshotDto snapshot = service.createSnapshot(7L, null, 120, true);

    assertThat(snapshot.snapshotId()).isEqualTo(42L);
    verify(graphService, times(1)).buildForUser(7L, null, 120);
  }

  @Test
  void buildsMemoryFromSuppliedGraphWithoutLoadingItAgain() {
    LearningEvidenceGraphService graphService = mock(LearningEvidenceGraphService.class);
    LearningEvidenceGraphDto graph =
        new LearningEvidenceGraphDto(
            7L,
            "PY43-KC-001",
            List.of(),
            List.of(),
            new EvidenceGraphMetricsDto(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of());
    EvidenceMemoryService service =
        new EvidenceMemoryService(graphService, mock(EvidenceMemoryRepository.class), mock(JdbcTemplate.class));

    EvidenceMemorySnapshotDto snapshot = service.createSnapshot(graph);

    assertThat(snapshot.focusKc()).isEqualTo("PY43-KC-001");
    verifyNoInteractions(graphService);
  }
}
