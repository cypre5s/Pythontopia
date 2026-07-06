package com.pythontopia.tutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 学生采纳某讲法（因材施教闭环）：上报后回流到画像 cognitive_style 维度。
 *
 * @param style 讲法风格，限 {@code visual}/{@code logical}/{@code trial}
 * @param kcCode 可选当前知识点
 */
public record StyleEngagedRequest(
    @NotBlank @Pattern(regexp = "visual|logical|trial") String style,
    @Size(max = 32) String kcCode) {}
