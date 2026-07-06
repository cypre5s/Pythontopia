package com.pythontopia.workshop.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ResourceCreateRequest(
    @NotEmpty @Size(max = 10) List<@NotNull String> kcCodes,
    @NotNull ResourceType type,
    @Size(max = 512) String focusHint) {}
