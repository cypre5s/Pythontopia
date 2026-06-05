package com.pythontopia.learner.probe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 世界内探针选择上报。仅携带目录键，由服务端 {@code ProbeCatalog} 解析为画像信号。
 *
 * @param probeKey 探针目录键，如 {@code cog_intro}
 * @param choiceId 选项键，如 {@code visual}
 */
public record ProbeRequest(
    @NotBlank @Size(max = 32) String probeKey, @NotBlank @Size(max = 32) String choiceId) {}
