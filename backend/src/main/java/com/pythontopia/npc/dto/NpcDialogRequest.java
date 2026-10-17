package com.pythontopia.npc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NpcDialogRequest(@NotBlank @Size(max = 2000) String message) {}
