package com.pythontopia.learner.evidence;

import java.util.List;

public record EvidenceMemorySignalDto(
    String kcCode,
    String signalKind,
    double directScore,
    double propagatedScore,
    double priorityScore,
    String relation,
    List<Long> evidenceEventIds,
    List<Long> checkIds,
    List<Long> pageIds,
    String retentionPolicy,
    String reason) {}
