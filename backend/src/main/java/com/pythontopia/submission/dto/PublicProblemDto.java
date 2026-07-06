package com.pythontopia.submission.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PublicProblemDto(
    Long id,
    Long packId,
    String kcCode,
    String title,
    String statementMarkdown,
    String inputDescription,
    String outputDescription,
    String difficulty,
    JsonNode samples) {}
