package com.pythontopia.learner.reflection.graph;

import java.util.List;

/** POST {graphUrl}/agents/reflection 响应体（与 graph {@code schemas/reflection.ReflectionOutput} 对齐）。 */
public record ReflectionGraphResponse(List<InsightPayload> insights) {

  public List<InsightPayload> safeInsights() {
    return insights == null ? List.of() : insights;
  }
}
