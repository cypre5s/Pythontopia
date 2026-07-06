package com.pythontopia.orchestrator.dto;

import jakarta.validation.constraints.Size;

/**
 * 学习总管对话历史中的一条消息。
 *
 * <p>{@code role} 取值通常为 {@code user} / {@code assistant} / {@code system}；{@code content}
 * 为该轮文本。两者均透传给 Python graph 服务（snake_case 由 {@code role}/{@code content} 直接对齐）。
 */
public record OrchestratorTurnMessage(
    @Size(max = 32) String role, @Size(max = 8000) String content) {}
