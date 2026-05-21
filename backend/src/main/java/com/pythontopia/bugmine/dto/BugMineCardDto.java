package com.pythontopia.bugmine.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record BugMineCardDto(
    Long id,
    Long sessionId,
    BugMineCardType cardType,
    JsonNode payload,
    boolean revealed,
    OffsetDateTime createdAt) {}
