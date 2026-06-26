package com.pythontopia.workshop.history;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WorkshopChatHistoryRecordRequest(
    @NotBlank @Size(max = 64) String conversationId,
    @NotBlank @Size(max = 4000) String userText,
    @NotBlank @Size(max = 32) String intent,
    @NotBlank
        @Pattern(
            regexp =
                "answer|resource|video|browse|error|clarify|plan|progress|mystudy|navigate|secondary")
        String responseKind,
    @Size(max = 64) String toolLabel,
    @Size(max = 20000) String responseText,
    JsonNode responsePayload) {}
