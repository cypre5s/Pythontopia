package com.pythontopia.tutor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TutorWorkflowCheckpointRequest(
    @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}")
        @JsonProperty("request_fingerprint")
        String requestFingerprint,
    @NotNull @JsonProperty("checkpoint") JsonNode checkpoint) {}
