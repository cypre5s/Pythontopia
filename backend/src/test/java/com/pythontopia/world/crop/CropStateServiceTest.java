package com.pythontopia.world.crop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySnapshot;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CropStateServiceTest {

  private CropTileRepository repository;
  private MasteryEvaluator masteryEvaluator;
  private CropStateService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(CropTileRepository.class);
    masteryEvaluator = Mockito.mock(MasteryEvaluator.class);
    service = new CropStateService(repository, masteryEvaluator);
  }

  @Test
  void statusMappingPerThreshold() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    assertThat(service.statusFor(new BigDecimal("0.05"), now)).isEqualTo(CropStatus.SEED);
    assertThat(service.statusFor(new BigDecimal("0.30"), now)).isEqualTo(CropStatus.SPROUT);
    assertThat(service.statusFor(new BigDecimal("0.55"), now)).isEqualTo(CropStatus.MATURE);
    assertThat(service.statusFor(new BigDecimal("0.85"), now)).isEqualTo(CropStatus.MATURE);
  }

  @Test
  void staleSeedBecomesWithered() {
    OffsetDateTime old = OffsetDateTime.now(ZoneOffset.UTC).minusDays(40);
    assertThat(service.statusFor(new BigDecimal("0.10"), old)).isEqualTo(CropStatus.WITHERED);
  }

  @Test
  void applyMasteryUpdateUpgradesCrop() {
    when(masteryEvaluator.snapshot(1L, "PY43-KC-001"))
        .thenReturn(
            Optional.of(
                new MasterySnapshot(
                    1L,
                    1L,
                    "PY43-KC-001",
                    new BigDecimal("0.300"),
                    new BigDecimal("0.500"),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    OffsetDateTime.now(ZoneOffset.UTC))));
    when(repository.findOne(1L, 43, "PY43-KC-001"))
        .thenReturn(
            Optional.of(
                new CropTile(
                    1L,
                    1L,
                    2,
                    2,
                    "PY43-KC-001",
                    0,
                    BigDecimal.ZERO,
                    CropStatus.SEED,
                    true,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    43)));

    service.applyMasteryUpdate(1L, "PY43-KC-001");

    verify(repository)
        .updateMasteryAndStatus(
            eq(1L), eq(43), eq("PY43-KC-001"), any(), eq(CropStatus.SPROUT), eq(1));
  }

  @Test
  void getMapHidesStatusOfLockedTiles() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    when(repository.findByUser(1L, 43))
        .thenReturn(
            List.of(
                new CropTile(
                    1L,
                    1L,
                    2,
                    2,
                    "PY43-KC-001",
                    0,
                    BigDecimal.ZERO,
                    CropStatus.SEED,
                    true,
                    now,
                    now,
                    43),
                new CropTile(
                    2L,
                    1L,
                    3,
                    2,
                    "PY43-KC-002",
                    0,
                    BigDecimal.ZERO,
                    CropStatus.SEED,
                    false,
                    null,
                    now,
                    43)));

    var map = service.getMap(1L);

    assertThat(map).hasSize(2);
    assertThat(map.get(0).status()).isEqualTo("SEED");
    assertThat(map.get(1).status()).isEqualTo("LOCKED");
  }
}
