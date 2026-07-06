package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 课件页阅读打点。 */
public record PageReadRequest(@NotNull @Positive Long pageId) {}
