package com.pythontopia.learner.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MasteryEvaluatorTest {

  private MasteryRepository repository;
  private MasteryProperties properties;
  private com.pythontopia.learner.mastery.review.ReviewStateService reviewStateService;
  private MasteryEvaluator evaluator;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(MasteryRepository.class);
    reviewStateService =
        Mockito.mock(com.pythontopia.learner.mastery.review.ReviewStateService.class);
    properties =
        new MasteryProperties(
            14,
            new BigDecimal("0.05"),
            new BigDecimal("0.02"),
            Map.of(
                "RESOURCE_READ", new BigDecimal("0.10"),
                "PRACTICE_PASS", new BigDecimal("0.20"),
                "PRACTICE_FAIL", new BigDecimal("-0.05"),
                "DEBUG_FIX", new BigDecimal("0.15"),
                "MANUAL_OVERRIDE", BigDecimal.ZERO,
                "DECAY", BigDecimal.ZERO,
                "BOOTSTRAP", BigDecimal.ZERO));
    evaluator = new MasteryEvaluator(repository, properties, reviewStateService);
  }

  @Test
  void applyEventCreatesSnapshotAndEvent() {
    when(repository.findSnapshot(1L, "PY43-KC-001")).thenReturn(Optional.empty());

    MasterySnapshot snapshot =
        evaluator.applyEvent(1L, "PY43-KC-001", MasterySource.RESOURCE_READ, null, "test");

    assertThat(snapshot.masteryScore()).isEqualByComparingTo("0.100");
    assertThat(snapshot.confidence()).isEqualByComparingTo("0.050");
    verify(repository)
        .upsertSnapshot(eq(1L), eq("PY43-KC-001"), any(), any(), any(OffsetDateTime.class));
    verify(repository)
        .insertEvent(
            eq(1L), eq("PY43-KC-001"), eq(MasterySource.RESOURCE_READ), any(), eq("test"));
  }

  @Test
  void applyEventClampsScoreAtMax() {
    when(repository.findSnapshot(1L, "PY43-KC-001"))
        .thenReturn(
            Optional.of(
                new MasterySnapshot(
                    11L,
                    1L,
                    "PY43-KC-001",
                    new BigDecimal("0.950"),
                    new BigDecimal("0.700"),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    OffsetDateTime.now(ZoneOffset.UTC))));

    MasterySnapshot snapshot =
        evaluator.applyEvent(1L, "PY43-KC-001", MasterySource.PRACTICE_PASS, null, "pass");

    assertThat(snapshot.masteryScore()).isEqualByComparingTo("1.000");
  }

  @Test
  void applyEventClampsScoreAtMin() {
    when(repository.findSnapshot(1L, "PY43-KC-001"))
        .thenReturn(
            Optional.of(
                new MasterySnapshot(
                    11L,
                    1L,
                    "PY43-KC-001",
                    new BigDecimal("0.030"),
                    new BigDecimal("0.200"),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    OffsetDateTime.now(ZoneOffset.UTC))));

    MasterySnapshot snapshot =
        evaluator.applyEvent(1L, "PY43-KC-001", MasterySource.PRACTICE_FAIL, null, "fail");

    assertThat(snapshot.masteryScore()).isEqualByComparingTo("0.000");
  }

  @Test
  void decayHalvesAfterHalfLife() {
    OffsetDateTime old = OffsetDateTime.now(ZoneOffset.UTC).minusDays(14);
    when(repository.findSnapshot(1L, "PY43-KC-001"))
        .thenReturn(
            Optional.of(
                new MasterySnapshot(
                    1L,
                    1L,
                    "PY43-KC-001",
                    new BigDecimal("0.800"),
                    new BigDecimal("0.600"),
                    old,
                    old)));

    MasterySnapshot snapshot = evaluator.decay(1L, "PY43-KC-001", 14L);

    assertThat(snapshot.masteryScore()).isEqualByComparingTo("0.400");
    verify(repository)
        .insertEvent(eq(1L), eq("PY43-KC-001"), eq(MasterySource.DECAY), any(), anyString());
  }

  @Test
  void decayAllIteratesEverySnapshot() {
    OffsetDateTime stale = OffsetDateTime.now(ZoneOffset.UTC).minusDays(28);
    when(repository.findAll(1L))
        .thenReturn(
            List.of(
                new MasterySnapshot(
                    1L,
                    1L,
                    "PY43-KC-001",
                    new BigDecimal("0.600"),
                    new BigDecimal("0.400"),
                    stale,
                    stale),
                new MasterySnapshot(
                    2L,
                    1L,
                    "PY43-KC-002",
                    new BigDecimal("0.200"),
                    new BigDecimal("0.200"),
                    stale,
                    stale)));
    when(repository.findSnapshot(eq(1L), anyString()))
        .thenAnswer(invocation -> Optional.of(snapshotFor(invocation.getArgument(1))));

    evaluator.decayAll(1L);

    verify(repository, times(2))
        .insertEvent(anyLong(), anyString(), eq(MasterySource.DECAY), any(), anyString());
  }

  private MasterySnapshot snapshotFor(String kcCode) {
    OffsetDateTime stale = OffsetDateTime.now(ZoneOffset.UTC).minusDays(28);
    return new MasterySnapshot(
        1L, 1L, kcCode, new BigDecimal("0.600"), new BigDecimal("0.400"), stale, stale);
  }
}
