package com.pythontopia.browse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/browse/runs} 请求体。<b>只接收 {@code goal} 与可选 {@code maxSteps}</b>——白名单域由
 * 服务端注入，前端无法指定可访问域（§8.6 完整性 / 防滥用）。
 *
 * @param goal 查证目标（自然语言，必填，≤500 字符对齐 V170 列宽）
 * @param maxSteps 有界步数上限（可选，缺省 {@code 6}；上界 {@code 12} 防滥用，保持「有界」红线）
 */
public record BrowseRunCreateRequest(
    @NotBlank @Size(max = 500) String goal, @Min(1) @Max(12) Integer maxSteps) {}
