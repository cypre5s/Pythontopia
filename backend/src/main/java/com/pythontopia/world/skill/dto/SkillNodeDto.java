package com.pythontopia.world.skill.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.world.skill.SkillNode;
import java.util.List;

public record SkillNodeDto(
    String skillKey,
    String displayName,
    String description,
    List<String> kcCodes,
    JsonNode unlockCondition,
    String iconUrl,
    int tier) {

  public static SkillNodeDto from(SkillNode node) {
    return new SkillNodeDto(
        node.skillKey(),
        node.displayName(),
        node.description(),
        node.kcCodes(),
        node.unlockCondition(),
        node.iconUrl(),
        node.tier() == null ? 1 : node.tier());
  }
}
