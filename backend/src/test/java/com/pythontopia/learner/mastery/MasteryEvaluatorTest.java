package com.pythontopia.learner.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
            eq(1L), eq("PY43-KC-001"), eq(MasterySource.RESOURCE_READ), any(), any(), eq("test"));
  }

  @Test
  void duplicateVerifiedAttemptDoesNotUpdateMasteryOrReviewStateAgain() {
    MasterySnapshot existing =
        new MasterySnapshot(
            11L,
            1L,
            "PY43-KC-001",
            new BigDecimal("0.400"),
            new BigDecimal("0.300"),
            OffsetDateTime.now(ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC));
    when(repository.findSnapshot(1L, "PY43-KC-001")).thenReturn(Optional.of(existing));
    when(repository.insertEventIfNew(
            anyLong(), anyString(), any(MasterySource.class), any(), any(), anyString(), anyString()))
        .thenReturn(false);

    MasterySnapshot result =
        evaluator.applyEvent(
            1L,
            "PY43-KC-001",
            MasterySource.DEBUG_FIX,
            null,
            "debug:42",
            "submission:900");

    assertThat(result).isSameAs(existing);
    verify(repository, never())
        .upsertSnapshot(anyLong(), anyString(), any(), any(), any(OffsetDateTime.class));
    verify(reviewStateService, never()).onMasteryEvent(anyLong(), anyString(), any(), any());
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
        .insertEvent(eq(1L), eq("PY43-KC-001"), eq(MasterySource.DECAY), any(), any(), anyString());
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
        .insertEvent(anyLong(), anyString(), eq(MasterySource.DECAY), any(), any(), anyString());
  }

  private MasterySnapshot snapshotFor(String kcCode) {
    OffsetDateTime stale = OffsetDateTime.now(ZoneOffset.UTC).minusDays(28);
    return new MasterySnapshot(
        1L, 1L, kcCode, new BigDecimal("0.600"), new BigDecimal("0.400"), stale, stale);
  }

  @Test
  void hardPassGainsMoreThanEasyPass() {
    when(repository.findSnapshot(1L, "KC01")).thenReturn(Optional.empty());
    MasterySnapshot hard = evaluator.applyEventWithDifficulty(
        1L, "KC01", MasterySource.PRACTICE_PASS, null, "hard", "high");
    MasterySnapshot easy = evaluator.applyEventWithDifficulty(
        1L, "KC01", MasterySource.PRACTICE_PASS, null, "easy", "low");
    assertThat(hard.masteryScore()).isGreaterThan(easy.masteryScore());
  }

  @Test
  void easyFailPenalizesMoreThanHardFail() {
    when(repository.findSnapshot(1L, "KC02"))
        .thenReturn(Optional.of(new MasterySnapshot(1L, 1L, "KC02",
            new BigDecimal("0.700"), new BigDecimal("0.500"),
            OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC))));
    MasterySnapshot easyFail = evaluator.applyEventWithDifficulty(
        1L, "KC02", MasterySource.PRACTICE_FAIL, null, "easy", "low");
    MasterySnapshot hardFail = evaluator.applyEventWithDifficulty(
        1L, "KC02", MasterySource.PRACTICE_FAIL, null, "hard", "high");
    assertThat(easyFail.masteryScore()).isLessThan(hardFail.masteryScore());
  }

  @Test
  void nullDifficultySameAsMedium() {
    when(repository.findSnapshot(1L, "KC03")).thenReturn(Optional.empty());
    MasterySnapshot a = evaluator.applyEvent(1L, "KC03", MasterySource.PRACTICE_PASS, null, "no");
    MasterySnapshot b = evaluator.applyEventWithDifficulty(
        1L, "KC03", MasterySource.PRACTICE_PASS, null, "med", "medium");
    assertThat(a.masteryScore()).isEqualByComparingTo(b.masteryScore());
  }

  @Test
  void consecutiveHardFailuresDecreaseMastery() {
    when(repository.findSnapshot(1L, "KC04"))
        .thenReturn(Optional.of(new MasterySnapshot(1L, 1L, "KC04",
            new BigDecimal("0.800"), new BigDecimal("0.600"),
            OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC))));
    MasterySnapshot after1 = evaluator.applyEventWithDifficulty(
        1L, "KC04", MasterySource.PRACTICE_FAIL, null, "fail1", "hard");
    assertThat(after1.masteryScore()).isLessThan(new BigDecimal("0.800"));
    // 第二次失败：分数继续降
    when(repository.findSnapshot(1L, "KC04")).thenReturn(Optional.of(after1));
    MasterySnapshot after2 = evaluator.applyEventWithDifficulty(
        1L, "KC04", MasterySource.PRACTICE_FAIL, null, "fail2", "hard");
    assertThat(after2.masteryScore()).isLessThan(after1.masteryScore());
  }

  @Test
  void consecutiveFailuresDecreaseConfidence() {
    // 验收硬指标：连续失败 → 置信度下降（修复「越失败越自信」缺陷）。
    when(repository.findSnapshot(1L, "KC05"))
        .thenReturn(Optional.of(new MasterySnapshot(1L, 1L, "KC05",
            new BigDecimal("0.800"), new BigDecimal("0.600"),
            OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC))));
    MasterySnapshot after1 = evaluator.applyEventWithDifficulty(
        1L, "KC05", MasterySource.PRACTICE_FAIL, null, "fail1", "hard");
    assertThat(after1.confidence()).isLessThan(new BigDecimal("0.600"));
    when(repository.findSnapshot(1L, "KC05")).thenReturn(Optional.of(after1));
    MasterySnapshot after2 = evaluator.applyEventWithDifficulty(
        1L, "KC05", MasterySource.PRACTICE_FAIL, null, "fail2", "hard");
    assertThat(after2.confidence()).isLessThan(after1.confidence());
  }

  @Test
  void passStillIncreasesConfidence() {
    when(repository.findSnapshot(1L, "KC06"))
        .thenReturn(Optional.of(new MasterySnapshot(1L, 1L, "KC06",
            new BigDecimal("0.300"), new BigDecimal("0.200"),
            OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC))));
    MasterySnapshot after = evaluator.applyEventWithDifficulty(
        1L, "KC06", MasterySource.PRACTICE_PASS, null, "pass", "medium");
    assertThat(after.confidence()).isGreaterThan(new BigDecimal("0.200"));
  }
}
