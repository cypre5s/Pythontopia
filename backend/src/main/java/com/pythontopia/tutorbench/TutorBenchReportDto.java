package com.pythontopia.tutorbench;

import com.pythontopia.learner.effect.LearningEffectSummaryDto;
import java.util.List;

public record TutorBenchReportDto(
    int totalCases,
    int passedCases,
    double passRate,
    LearningEffectSummaryDto learningEffect,
    List<TutorBenchCaseDto> cases) {}
