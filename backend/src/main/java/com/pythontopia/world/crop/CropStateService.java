package com.pythontopia.world.crop;

import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.world.crop.dto.CropTileDto;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CropStateService {

  static final BigDecimal THRESHOLD_SPROUT = new BigDecimal("0.2");
  static final BigDecimal THRESHOLD_MATURE = new BigDecimal("0.5");
  static final BigDecimal THRESHOLD_HARVEST = new BigDecimal("0.8");
  static final int WITHER_THRESHOLD_DAYS = 30;
  static final int DEFAULT_PACK_ID = 43;

  private final CropTileRepository repository;
  private final MasteryEvaluator masteryEvaluator;

  public CropStateService(CropTileRepository repository, MasteryEvaluator masteryEvaluator) {
    this.repository = repository;
    this.masteryEvaluator = masteryEvaluator;
  }

  public List<CropTileDto> getMap(Long userId) {
    return repository.findByUser(userId, DEFAULT_PACK_ID).stream()
        .map(
            tile ->
                new CropTileDto(
                    tile.tileX(),
                    tile.tileY(),
                    tile.kcCode(),
                    tile.unlocked() ? tile.status().name() : CropStatus.LOCKED.name(),
                    tile.unlocked(),
                    tile.masteryScore(),
                    tile.growthStage()))
        .toList();
  }

  public CropStatus statusFor(BigDecimal mastery, OffsetDateTime lastEventAt) {
    if (mastery == null) {
      return CropStatus.SEED;
    }
    if (mastery.compareTo(THRESHOLD_SPROUT) < 0) {
      if (isStale(lastEventAt)) {
        return CropStatus.WITHERED;
      }
      return CropStatus.SEED;
    }
    if (mastery.compareTo(THRESHOLD_MATURE) < 0) {
      return CropStatus.SPROUT;
    }
    return CropStatus.MATURE;
  }

  private boolean isStale(OffsetDateTime lastEventAt) {
    if (lastEventAt == null) {
      return false;
    }
    long days = Math.max(0, Duration.between(lastEventAt, OffsetDateTime.now(ZoneOffset.UTC)).toDays());
    return days >= WITHER_THRESHOLD_DAYS;
  }

  public void applyMasteryUpdate(Long userId, String kcCode) {
    Optional<MasterySnapshot> snapshot = masteryEvaluator.snapshot(userId, kcCode);
    if (snapshot.isEmpty()) {
      return;
    }
    Optional<CropTile> tile = repository.findOne(userId, DEFAULT_PACK_ID, kcCode);
    if (tile.isEmpty()) {
      return;
    }
    BigDecimal mastery = snapshot.get().masteryScore();
    CropStatus newStatus = statusFor(mastery, snapshot.get().lastEventAt());
    int growthStage;
    if (newStatus == CropStatus.SEED) {
      growthStage = 0;
    } else if (newStatus == CropStatus.SPROUT) {
      growthStage = 1;
    } else if (newStatus == CropStatus.MATURE) {
      growthStage = mastery.compareTo(THRESHOLD_HARVEST) >= 0 ? 3 : 2;
    } else if (newStatus == CropStatus.WITHERED) {
      growthStage = 0;
    } else {
      growthStage = tile.get().growthStage();
    }
    repository.updateMasteryAndStatus(userId, DEFAULT_PACK_ID, kcCode, mastery, newStatus, growthStage);
  }

  public void ensureTile(Long userId, String kcCode, int tileX, int tileY, boolean unlocked) {
    CropStatus initial = unlocked ? CropStatus.SEED : CropStatus.LOCKED;
    repository.insertIfMissing(userId, DEFAULT_PACK_ID, kcCode, tileX, tileY, unlocked, initial);
  }

  public void unlock(Long userId, String kcCode, int tileX, int tileY) {
    repository.unlock(userId, DEFAULT_PACK_ID, kcCode, tileX, tileY);
  }

  public int countTiles(Long userId) {
    return repository.countByUser(userId, DEFAULT_PACK_ID);
  }

  public int countUnlocked(Long userId) {
    return repository.countUnlocked(userId, DEFAULT_PACK_ID);
  }
}
