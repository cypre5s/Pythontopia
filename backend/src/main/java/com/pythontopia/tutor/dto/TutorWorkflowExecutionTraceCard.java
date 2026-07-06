package com.pythontopia.tutor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * 执行轨迹讲解卡（EXECUTION_TRACE，graph {@code card_key=execution_trace}）的 Java 契约投影。
 *
 * <p>字段 snake_case 对齐 graph {@code schemas/tutor_workflow.py ExecutionTraceCard}。对给定代码（或
 * 无代码时对题目样例处理过程）做逐步轨迹讲解；若代码有 bug，{@code steps} 如实呈现其「当前」行为，
 * 不替学生改对、不给完整修正代码（防泄答 + 调试导向）。卡片 payload 仍由 {@link TutorWorkflowCardDto}
 * 原始 {@code JsonNode} 透传，本 DTO 仅作 Java 侧强类型契约。
 */
public record TutorWorkflowExecutionTraceCard(
    @JsonProperty("card_id") String cardId,
    String summary,
    @JsonProperty("trace_target") String traceTarget,
    @JsonProperty("input_used") String inputUsed,
    List<Step> steps,
    @JsonProperty("key_insight") String keyInsight,
    @JsonProperty("teaching_goal") String teachingGoal,
    @JsonProperty("checkpoint_prompt") String checkpointPrompt,
    @JsonProperty("mentor_role") String mentorRole,
    @JsonProperty("reflection_prompt") String reflectionPrompt) {

  /** 轨迹表一行：步号、代码位置、动作、变量快照（值类型不限）、备注。 */
  public record Step(
      int step,
      @JsonProperty("line_ref") String lineRef,
      String action,
      Map<String, Object> variables,
      String note) {}
}
