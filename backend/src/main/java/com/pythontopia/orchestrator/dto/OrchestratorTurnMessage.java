package com.pythontopia.orchestrator.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 学习总管对话历史中的一条消息。
 *
 * <p>{@code role} 只接受 {@code user} / {@code assistant}；{@code content} 为该轮文本。两者均透传给 Python graph
 * 服务（snake_case 由 {@code role}/{@code content} 直接对齐）。
 */
public record OrchestratorTurnMessage(
    @NotNull @Pattern(regexp = "user|assistant") @Size(max = 32) String role,
    @NotNull @Size(max = 8000) String content) {}
