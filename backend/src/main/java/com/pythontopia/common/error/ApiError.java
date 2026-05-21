package com.pythontopia.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
    String code, String message, List<String> details, String traceId, Instant timestamp) {

  public static ApiError of(String code, String message, List<String> details, String traceId) {
    return new ApiError(code, message, List.copyOf(details), traceId, Instant.now());
  }
}
