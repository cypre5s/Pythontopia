package com.pythontopia.learner.evidence;

public record EvidenceGraphMetricsDto(
    int nodeCount,
    int edgeCount,
    int touchedKcCount,
    int groundedPageCount,
    double checkCoverageRate,
    double masteryEvidenceRate,
    double connectedEvidenceRate,
    double remediationRate,
    double graphCompletenessScore,
    int isolatedEventCount) {}
