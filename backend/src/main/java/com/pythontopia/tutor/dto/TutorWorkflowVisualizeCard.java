package com.pythontopia.tutor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 可视化教学图卡（VISUALIZE，graph {@code card_key=visualize}）的 Java 契约投影。
 *
 * <p>字段 snake_case 对齐 graph {@code schemas/tutor_workflow.py VisualizeCard}。本仓「graph 零回调
 * Java」：不调用 Java 出图，{@code diagram} 为 LLM 产出的文本可渲染图源码（默认 {@code format=mermaid}，
 * 前端可直接渲染；{@code altText} 为降级 / 无障碍文本）。图只画题目结构 / 关系 / 检查点（防泄答）。
 * 卡片 payload 仍由 {@link TutorWorkflowCardDto} 原始 {@code JsonNode} 透传，本 DTO 仅作 Java 侧强类型契约。
 */
public record TutorWorkflowVisualizeCard(
    @JsonProperty("card_id") String cardId,
    String intent,
    String title,
    String format,
    String diagram,
    @JsonProperty("alt_text") String altText,
    String caption,
    @JsonProperty("teaching_goal") String teachingGoal,
    @JsonProperty("checkpoint_prompt") String checkpointPrompt,
    @JsonProperty("mentor_role") String mentorRole,
    @JsonProperty("reflection_prompt") String reflectionPrompt) {}
