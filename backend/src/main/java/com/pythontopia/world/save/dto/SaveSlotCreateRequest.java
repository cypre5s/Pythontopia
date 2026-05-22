package com.pythontopia.world.save.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveSlotCreateRequest(@NotBlank @Size(max = 64) String displayName) {}
