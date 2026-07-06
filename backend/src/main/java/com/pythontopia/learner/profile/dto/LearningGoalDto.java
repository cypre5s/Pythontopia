package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record LearningGoalDto(
    String kind,
    @JsonAlias("deadline_iso") String deadlineIso,
    @JsonAlias("target_kc_codes") List<String> targetKcCodes) {}
