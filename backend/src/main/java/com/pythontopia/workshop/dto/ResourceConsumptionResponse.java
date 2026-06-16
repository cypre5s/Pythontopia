package com.pythontopia.workshop.dto;

import java.util.List;

public record ResourceConsumptionResponse(
    Long resourceId,
    List<String> kcCodes,
    String nextAction,
    String prompt) {}
