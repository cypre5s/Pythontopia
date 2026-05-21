package com.pythontopia.learner.mastery;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pythontopia.mastery")
public record MasteryProperties(
    int halfLifeDays,
    BigDecimal confidenceGainPerEvent,
    BigDecimal confidenceLossPerDecay,
    Map<String, BigDecimal> deltas) {

  public MasteryProperties {
    if (halfLifeDays <= 0) {
      halfLifeDays = 14;
    }
    if (confidenceGainPerEvent == null) {
      confidenceGainPerEvent = new BigDecimal("0.05");
    }
    if (confidenceLossPerDecay == null) {
      confidenceLossPerDecay = new BigDecimal("0.02");
    }
    if (deltas == null || deltas.isEmpty()) {
      deltas =
          Map.of(
              "RESOURCE_READ", new BigDecimal("0.10"),
              "PRACTICE_PASS", new BigDecimal("0.20"),
              "PRACTICE_FAIL", new BigDecimal("-0.05"),
              "DEBUG_FIX", new BigDecimal("0.15"),
              "MANUAL_OVERRIDE", new BigDecimal("0.00"),
              "DECAY", new BigDecimal("0.00"),
              "BOOTSTRAP", new BigDecimal("0.00"));
    }
  }

  public BigDecimal defaultDelta(MasterySource source) {
    return deltas.getOrDefault(source.name(), BigDecimal.ZERO);
  }
}
