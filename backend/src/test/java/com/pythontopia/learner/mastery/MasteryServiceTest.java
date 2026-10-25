package com.pythontopia.learner.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MasteryServiceTest {

  private final MasteryEvaluator evaluator = mock(MasteryEvaluator.class);
  private final MasteryService service = new MasteryService(evaluator);

  @Test
  void loadsLatestSourcesForAllSnapshotsInOneLookup() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    when(evaluator.assessedKcCodes(7L)).thenReturn(Set.of("KC01"));
    when(evaluator.snapshots(7L))
        .thenReturn(
            List.of(
                snapshot(1L, 7L, "KC01", now),
                snapshot(2L, 7L, "KC02", null)));
    when(evaluator.latestEventSourcesByKc(7L))
        .thenReturn(Map.of("KC01", MasterySource.PRACTICE_PASS));

    var result = service.listSnapshots(7L);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).latestSource()).isEqualTo("PRACTICE_PASS");
    assertThat(result.get(0).assessed()).isTrue();
    assertThat(result.get(1).latestSource()).isNull();
    assertThat(result.get(1).assessed()).isFalse();
    verify(evaluator).latestEventSourcesByKc(7L);
    verify(evaluator, never()).recentEvents(anyLong(), anyString(), anyInt());
  }

  @Test
  void skipsEvidenceQueriesWhenThereAreNoSnapshots() {
    when(evaluator.snapshots(7L)).thenReturn(List.of());

    assertThat(service.listSnapshots(7L)).isEmpty();

    verify(evaluator, never()).assessedKcCodes(7L);
    verify(evaluator, never()).latestEventSourcesByKc(7L);
  }

  private static MasterySnapshot snapshot(
      long id, long userId, String kcCode, OffsetDateTime lastEventAt) {
    return new MasterySnapshot(
        id,
        userId,
        kcCode,
        new BigDecimal("0.400"),
        new BigDecimal("0.300"),
        lastEventAt,
        lastEventAt);
  }
}
