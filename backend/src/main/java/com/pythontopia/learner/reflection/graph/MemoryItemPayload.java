package com.pythontopia.learner.reflection.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 发送给 graph ReflectionAgent 的单条记忆（与 graph {@code schemas/reflection.MemoryItem} 对齐）。 */
public record MemoryItemPayload(
    @JsonProperty("event_id") long eventId,
    @JsonProperty("event_type") String eventType,
    @JsonProperty("kc_codes") List<String> kcCodes,
    int importance,
    @JsonProperty("created_at") String createdAt,
    String summary) {}
