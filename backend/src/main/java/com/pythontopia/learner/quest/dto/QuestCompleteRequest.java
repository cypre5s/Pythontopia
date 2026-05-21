package com.pythontopia.learner.quest.dto;

import jakarta.validation.constraints.Size;

public record QuestCompleteRequest(@Size(max = 256) String note) {}
