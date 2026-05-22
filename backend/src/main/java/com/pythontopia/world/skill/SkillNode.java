package com.pythontopia.world.skill;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;

public record SkillNode(
    String skillKey,
    String displayName,
    String description,
    List<String> kcCodes,
    JsonNode unlockCondition,
    String iconUrl,
    Integer tier,
    OffsetDateTime createTime) {}
