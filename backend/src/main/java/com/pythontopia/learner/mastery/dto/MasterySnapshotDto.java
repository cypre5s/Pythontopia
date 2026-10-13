package com.pythontopia.learner.mastery.dto;

import java.math.BigDecimal;

public record MasterySnapshotDto(
    String kcCode,
    BigDecimal masteryScore,
    BigDecimal confidence,
    String lastEventAt,
    String latestSource,
    boolean assessed) {

  public MasterySnapshotDto(
      String kcCode,
      BigDecimal masteryScore,
      BigDecimal confidence,
      String lastEventAt,
      String latestSource) {
    this(kcCode, masteryScore, confidence, lastEventAt, latestSource, false);
  }
}
