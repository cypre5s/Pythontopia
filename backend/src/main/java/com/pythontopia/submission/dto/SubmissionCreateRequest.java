package com.pythontopia.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmissionCreateRequest(
    @NotNull Long problemId,
    @NotBlank @Size(max = 8192) String code,
    Long questId,
    SubmissionSource source,
    @Size(max = 64) @Pattern(regexp = "wf-[0-9]+-[0-9]+-[0-9a-f]{1,8}")
        String tutorWorkflowSessionKey) {

  public SubmissionCreateRequest(
      Long problemId, String code, Long questId, SubmissionSource source) {
    this(problemId, code, questId, source, null);
  }
}
