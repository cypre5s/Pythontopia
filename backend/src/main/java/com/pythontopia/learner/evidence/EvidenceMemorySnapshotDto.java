package com.pythontopia.learner.evidence;

import java.util.List;

public record EvidenceMemorySnapshotDto(
    Long snapshotId,
    Long userId,
    String focusKc,
    int directSignalCount,
    int propagatedSignalCount,
    int retainedEvidenceCount,
    double graphMemoryScore,
    double naiveMemoryScore,
    List<EvidenceMemorySignalDto> signals) {}
