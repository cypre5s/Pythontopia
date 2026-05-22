package com.pythontopia.world.skill;

import java.time.OffsetDateTime;

public record LearnerSkill(
    Long id, Long userId, String skillKey, OffsetDateTime unlockedAt, Integer proficiency) {}
