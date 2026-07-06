package com.pythontopia.workshop.history;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record WorkshopChatHistoryTurnDto(
    String id,
    String conversationId,
    String userText,
    String intent,
    String responseKind,
    String toolLabel,
    String responseText,
    JsonNode responsePayload,
    List<Integer> evidencePageIds,
    String groundingStatus,
    Instant createdAt) {}
