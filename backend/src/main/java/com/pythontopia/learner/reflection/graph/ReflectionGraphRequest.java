package com.pythontopia.learner.reflection.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** POST {graphUrl}/agents/reflection 请求体（与 graph {@code schemas/reflection.ReflectionInput} 对齐）。 */
public record ReflectionGraphRequest(
    @JsonProperty("user_id") Long userId, List<MemoryItemPayload> memories) {}
