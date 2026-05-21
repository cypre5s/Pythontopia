package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record LearningPreferenceDto(
    @JsonAlias("media_priority") List<String> mediaPriority,
    @JsonAlias("session_minutes_per_day") Integer sessionMinutesPerDay) {}
