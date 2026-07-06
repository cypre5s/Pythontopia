package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record EmotionPersistenceDto(Double resilience, @JsonAlias("recent_mood") String recentMood) {}
