package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 无尽模式一轮小结上报体（{@code POST /api/mainline/endless/summary}，r15 §7/§9）。
 *
 * <p>触发 {@code ENDLESS_RECOVERY} 补星判定：本章范围单轮答对 ≥ 8 且正确率 ≥ 80%（后端权威校验，
 * 每日每章至多 1 次有效判定，防刷）。
 */
public record EndlessSummaryRequest(
    @NotNull @Min(1) @Max(7) Integer chapter,
    @NotNull @Min(0) Integer correct,
    @NotNull @Min(1) Integer total,
    @NotNull @Min(0) Integer streak) {}
