package com.pythontopia.learner.evaluation.dto;

import java.math.BigDecimal;

public record EvaluationMetricDto(
    String metricKey,
    BigDecimal numerator,
    BigDecimal denominator,
    BigDecimal value,
    String displayUnit,
    String notes) {}
