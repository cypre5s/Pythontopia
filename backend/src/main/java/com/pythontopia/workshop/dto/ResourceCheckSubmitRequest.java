package com.pythontopia.workshop.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResourceCheckSubmitRequest(
    @NotBlank @Size(max = 64) String kcCode,
    @NotNull ResourceCheckEvidenceType evidenceType,
    @Size(max = 2000) String answerText,
    JsonNode operationEvidence) {}
