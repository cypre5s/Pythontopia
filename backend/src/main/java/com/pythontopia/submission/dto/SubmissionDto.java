package com.pythontopia.submission.dto;

import java.time.OffsetDateTime;

public record SubmissionDto(
    Long id,
    Long userId,
    Long problemId,
    Long questId,
    SubmissionSource source,
    String language,
    String code,
    OffsetDateTime submittedAt,
    SubmissionStatus status,
    Integer runtimeMs,
    Integer memoryKb) {}
