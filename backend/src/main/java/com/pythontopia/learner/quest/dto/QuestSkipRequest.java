package com.pythontopia.learner.quest.dto;

import jakarta.validation.constraints.Size;

public record QuestSkipRequest(@Size(max = 256) String reason) {}
