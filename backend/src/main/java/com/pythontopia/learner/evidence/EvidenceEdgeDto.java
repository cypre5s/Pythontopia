package com.pythontopia.learner.evidence;

import java.util.Map;
import java.util.LinkedHashMap;

public record EvidenceEdgeDto(
    String fromId, String toId, String relation, double weight, Map<String, Object> attributes) {
  public EvidenceEdgeDto {
    attributes = clean(attributes);
  }

  private static Map<String, Object> clean(Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> copy = new LinkedHashMap<>();
    attributes.forEach(
        (key, value) -> {
          if (key != null && value != null) {
            copy.put(key, value);
          }
        });
    return Map.copyOf(copy);
  }
}
