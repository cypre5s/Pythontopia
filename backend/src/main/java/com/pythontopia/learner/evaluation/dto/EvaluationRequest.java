package com.pythontopia.learner.evaluation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record EvaluationRequest(@Min(1) @Max(30) Integer periodDays) {}
