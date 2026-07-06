package com.pythontopia.world.skill.dto;

import com.pythontopia.world.skill.LearnerSkill;
import java.time.OffsetDateTime;

public record LearnerSkillDto(
    String skillKey, OffsetDateTime unlockedAt, int proficiency) {

  public static LearnerSkillDto from(LearnerSkill skill) {
    return new LearnerSkillDto(
        skill.skillKey(),
        skill.unlockedAt(),
        skill.proficiency() == null ? 0 : skill.proficiency());
  }
}
