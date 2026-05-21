package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ProfileRevisionDto(Long revisionId, String source, JsonNode diff, String createdAt) {}
