package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 无尽一轮小结上报（POST /api/mainline/endless/summary，§7/§9）：触发 ENDLESS_RECOVERY 补星判定。 */
public record EndlessSummaryRequest(
    @NotNull @Min(1) @Max(7) Integer chapter,
    @NotNull @Min(0) Integer correct,
    @NotNull @Min(1) Integer total,
    @NotNull @Min(0) Integer streak) {}
