package com.pythontopia.classroom.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public record ResourceUsageDto(
    LocalDate periodStart,
    LocalDate periodEnd,
    List<ResourceTypeBucket> totals,
    List<DailyBucket> daily) {

  public record ResourceTypeBucket(String resourceType, int count) {}

  public record DailyBucket(LocalDate day, String resourceType, int count) {}
}
