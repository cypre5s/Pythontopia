package com.pythontopia.submission.dto;

public record SubmissionPerformanceDto(
    Double runtimeBeatsPercent,
    Integer runtimeSampleCount,
    Double memoryBeatsPercent,
    Integer memorySampleCount) {

  public static SubmissionPerformanceDto hidden() {
    return new SubmissionPerformanceDto(null, null, null, null);
  }
}
