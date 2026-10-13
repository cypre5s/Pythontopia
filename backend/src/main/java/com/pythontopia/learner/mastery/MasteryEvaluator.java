package com.pythontopia.learner.mastery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** KC 掌握度快照的权威写入方：前端不得本地计算掌握度，一切以此服务为唯一数据源。 */
@Service
public class MasteryEvaluator {

  static final BigDecimal MIN = BigDecimal.ZERO;
  static final BigDecimal MAX = BigDecimal.ONE;

  private final MasteryRepository repository;
  private final MasteryProperties properties;
  private final com.pythontopia.learner.mastery.review.ReviewStateService reviewStateService;

  public MasteryEvaluator(
      MasteryRepository repository,
      MasteryProperties properties,
      com.pythontopia.learner.mastery.review.ReviewStateService reviewStateService) {
    this.repository = repository;
    this.properties = properties;
    this.reviewStateService = reviewStateService;
  }

  @Transactional
  public MasterySnapshot applyEvent(
      Long userId, String kcCode, MasterySource source, BigDecimal delta, String reason) {
    return applyEventWithDifficulty(userId, kcCode, source, delta, reason, null);
  }

  /** 难度加权：难题通过增益大（×1.5），易题失败惩罚重（÷0.7）。difficulty 为空时退化为普通模式。 */
  @Transactional
  public MasterySnapshot applyEventWithDifficulty(
      Long userId, String kcCode, MasterySource source,
      BigDecimal delta, String reason, String difficulty) {
    BigDecimal effectiveDelta = delta != null ? delta : properties.defaultDelta(source);
    if (difficulty != null && !difficulty.isBlank()) {
      effectiveDelta = MasteryProperties.difficultyWeightedDelta(effectiveDelta, difficulty);
    }
    repository.lockSnapshot(userId, kcCode);
    Optional<MasterySnapshot> existing = repository.findSnapshot(userId, kcCode);
    BigDecimal previousScore =
        existing.map(MasterySnapshot::masteryScore).orElse(BigDecimal.ZERO);
    BigDecimal previousConfidence =
        existing.map(MasterySnapshot::confidence).orElse(BigDecimal.ZERO);

    BigDecimal newScore = clamp(previousScore.add(effectiveDelta));
    BigDecimal newConfidence;
    if (source == MasterySource.DECAY) {
      newConfidence = clamp(previousConfidence.subtract(properties.confidenceLossPerDecay()));
    } else if (source == MasterySource.BOOTSTRAP) {
      newConfidence = previousConfidence;
    } else if (effectiveDelta.signum() < 0) {
      // 失败/挫折（如 PRACTICE_FAIL，effectiveDelta<0）置信度应下降，而非上升。
      // 修复前所有非 DECAY/BOOTSTRAP 事件一律加分，导致「越失败越自信」与验收冲突。
      newConfidence = clamp(previousConfidence.subtract(properties.confidenceLossPerDecay()));
    } else {
      newConfidence = clamp(previousConfidence.add(properties.confidenceGainPerEvent()));
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    repository.upsertSnapshot(userId, kcCode, scale(newScore), scale(newConfidence), now);
    repository.insertEvent(userId, kcCode, source, scale(effectiveDelta), reason);
    // 同事务演化 FSRS-lite 复习状态（成功/失败/重学）；DECAY/BOOTSTRAP/MANUAL 在 service 内被忽略。
    reviewStateService.onMasteryEvent(userId, kcCode, source, now);
    return new MasterySnapshot(
        existing.map(MasterySnapshot::id).orElse(null),
        userId,
        kcCode,
        scale(newScore),
        scale(newConfidence),
        now,
        now);
  }

  @Transactional
  public MasterySnapshot decay(Long userId, String kcCode, long days) {
    Optional<MasterySnapshot> existing = repository.findSnapshot(userId, kcCode);
    if (existing.isEmpty()) {
      return null;
    }
    BigDecimal previousScore = existing.get().masteryScore();
    if (previousScore.compareTo(BigDecimal.ZERO) <= 0) {
      return existing.get();
    }
    double factor = Math.pow(0.5d, (double) days / Math.max(1, properties.halfLifeDays()));
    BigDecimal newScore = clamp(previousScore.multiply(BigDecimal.valueOf(factor)));
    BigDecimal delta = newScore.subtract(previousScore);
    return applyEvent(
        userId,
        kcCode,
        MasterySource.DECAY,
        delta,
        "decay after " + days + "d, halfLife=" + properties.halfLifeDays());
  }

  @Transactional
  public void decayAll(Long userId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    for (MasterySnapshot snapshot : repository.findAll(userId)) {
      OffsetDateTime last =
          Optional.ofNullable(snapshot.lastEventAt()).orElse(snapshot.updatedAt());
      if (last == null) {
        continue;
      }
      long days = Math.max(0, Duration.between(last, now).toDays());
      if (days <= 0) {
        continue;
      }
      decay(userId, snapshot.kcCode(), days);
    }
  }

  public Optional<MasterySnapshot> snapshot(Long userId, String kcCode) {
    return repository.findSnapshot(userId, kcCode);
  }

  public List<MasterySnapshot> snapshots(Long userId) {
    return repository.findAll(userId);
  }

  public List<MasteryEventRecord> recentEvents(Long userId, String kcCode, int limit) {
    return repository.recentEvents(userId, kcCode, Math.max(1, Math.min(100, limit)));
  }

  static BigDecimal clamp(BigDecimal value) {
    if (value.compareTo(MIN) < 0) {
      return MIN;
    }
    if (value.compareTo(MAX) > 0) {
      return MAX;
    }
    return value;
  }

  static BigDecimal scale(BigDecimal value) {
    return value.setScale(3, RoundingMode.HALF_UP);
  }
}
