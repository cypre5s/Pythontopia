package com.pythontopia.learner.mastery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pythontopia.mastery")
public record MasteryProperties(
    int halfLifeDays,
    BigDecimal confidenceGainPerEvent,
    BigDecimal confidenceLossPerDecay,
    Map<String, BigDecimal> deltas) {

  static final BigDecimal DIFF_EASY = new BigDecimal("0.7");
  static final BigDecimal DIFF_MED = new BigDecimal("1.0");
  static final BigDecimal DIFF_HARD = new BigDecimal("1.5");

  public MasteryProperties {
    if (halfLifeDays <= 0) halfLifeDays = 14;
    if (confidenceGainPerEvent == null) confidenceGainPerEvent = new BigDecimal("0.05");
    if (confidenceLossPerDecay == null) confidenceLossPerDecay = new BigDecimal("0.02");
    if (deltas == null || deltas.isEmpty()) {
      deltas = Map.of(
          "RESOURCE_READ", new BigDecimal("0.10"),
          "PRACTICE_PASS", new BigDecimal("0.20"),
          "PRACTICE_FAIL", new BigDecimal("-0.05"),
          "DEBUG_FIX", new BigDecimal("0.15"),
          "MANUAL_OVERRIDE", BigDecimal.ZERO,
          "DECAY", BigDecimal.ZERO,
          "BOOTSTRAP", BigDecimal.ZERO);
    }
  }

  public BigDecimal defaultDelta(MasterySource source) {
    return deltas.getOrDefault(source.name(), BigDecimal.ZERO);
  }

  /** 题目难度系数：难题通过增益大（×1.5），易题失败惩罚重（÷0.7）。 */
  public static BigDecimal difficultyMultiplier(String difficulty) {
    if (difficulty == null) return DIFF_MED;
    return switch (difficulty.toLowerCase()) {
      case "low", "easy", "basic" -> DIFF_EASY;
      case "high", "hard" -> DIFF_HARD;
      default -> DIFF_MED;
    };
  }

  /** 通过时乘系数，失败时除系数。 */
  public static BigDecimal difficultyWeightedDelta(BigDecimal base, String difficulty) {
    BigDecimal m = difficultyMultiplier(difficulty);
    if (base.signum() > 0) return base.multiply(m).setScale(3, RoundingMode.HALF_UP);
    if (base.signum() < 0) return base.divide(m, 3, RoundingMode.HALF_UP);
    return base;
  }
}
