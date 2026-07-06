package com.pythontopia.tutor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 拼装挑战卡（PARSONS，graph {@code card_key=parsons}）的 Java 契约投影。
 *
 * <p>字段 snake_case 对齐 graph {@code schemas/tutor_workflow.py ParsonsCard}。本仓不依赖 Java FSRS
 * 出题：{@code blocks} / {@code distractors} / {@code correctOrder} 描述一道**独立子技能微练习**
 * （明确区别于当前题完整解法，防泄答），{@code correctOrder} 是该微练习的正确顺序（非当前题答案），
 * 供前端本地自检。卡片 payload 仍由 {@link TutorWorkflowCardDto} 原始 {@code JsonNode} 透传，本 DTO
 * 仅作 Java 侧强类型契约。
 */
public record TutorWorkflowParsonsCard(
    @JsonProperty("card_id") String cardId,
    @JsonProperty("drill_goal") String drillGoal,
    String prompt,
    List<Block> blocks,
    List<Block> distractors,
    @JsonProperty("correct_order") List<String> correctOrder,
    @JsonProperty("fading_level") int fadingLevel,
    @JsonProperty("success_check") String successCheck,
    @JsonProperty("teaching_goal") String teachingGoal,
    @JsonProperty("checkpoint_prompt") String checkpointPrompt,
    @JsonProperty("mentor_role") String mentorRole,
    @JsonProperty("reflection_prompt") String reflectionPrompt) {

  /** Parsons 代码积木：稳定 id、单行代码、缩进层级。 */
  public record Block(String id, String code, int indent) {}
}
