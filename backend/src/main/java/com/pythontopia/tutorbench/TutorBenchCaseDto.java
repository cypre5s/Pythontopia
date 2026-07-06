package com.pythontopia.tutorbench;

public record TutorBenchCaseDto(
    String name,
    boolean passed,
    double score,
    String reason) {}
