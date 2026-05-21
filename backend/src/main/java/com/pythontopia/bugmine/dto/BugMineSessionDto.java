package com.pythontopia.bugmine.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BugMineSessionDto(
    Long id,
    Long userId,
    Long submissionId,
    Long problemId,
    Long questId,
    BugMineSessionStatus status,
    int retryCount,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    List<BugMineCardDto> cards) {}
