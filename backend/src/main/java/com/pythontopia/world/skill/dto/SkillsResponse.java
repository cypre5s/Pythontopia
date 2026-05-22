package com.pythontopia.world.skill.dto;

import java.util.List;

public record SkillsResponse(List<SkillNodeDto> nodes, List<LearnerSkillDto> unlocked) {}
