package com.pythontopia.npc.dto;

import jakarta.validation.constraints.NotBlank;

public record NpcDialogRequest(@NotBlank String message) {}
