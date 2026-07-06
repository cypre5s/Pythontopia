package com.pythontopia.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmissionCreateRequest(
    @NotNull Long problemId,
    @NotBlank @Size(max = 8192) String code,
    Long questId,
    SubmissionSource source) {}
