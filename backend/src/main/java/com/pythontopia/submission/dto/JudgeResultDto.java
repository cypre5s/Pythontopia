package com.pythontopia.submission.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record JudgeResultDto(
    Long id,
    Long submissionId,
    Verdict verdict,
    int passedCases,
    int totalCases,
    String failureReason,
    JsonNode trace,
    OffsetDateTime createdAt) {}
