package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record LearnerProfileDto(
    Long profileId,
    Long userId,
    Long packId,
    String major,
    @JsonAlias("base_level")
    BaseLevelDto baseLevel,
    @JsonAlias("learning_goal")
    LearningGoalDto learningGoal,
    @JsonAlias("cognitive_style")
    CognitiveStyleDto cognitiveStyle,
    @JsonAlias("learning_preference")
    LearningPreferenceDto learningPreference,
    @JsonAlias("error_patterns")
    ErrorPatternsDto errorPatterns,
    @JsonAlias("practice_capability")
    PracticeCapabilityDto practiceCapability,
    @JsonAlias("emotion_persistence")
    EmotionPersistenceDto emotionPersistence,
    @JsonAlias("narrative_summary")
    String narrativeSummary) {}
