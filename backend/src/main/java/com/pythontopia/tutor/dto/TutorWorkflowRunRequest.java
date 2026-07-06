package com.pythontopia.tutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

/**
 * 导学工作流事件请求（前端 → Java）。
 *
 * <p>主线事件 ∈ READING / IDEATING / SKELETON / CODING / ERROR_FEEDBACK / AC_REVIEW / TRANSFER /
 * CHAT；辅助事件（不推进阶段）∈ KNOWLEDGE_REVIEW / EXECUTION_TRACE / VISUALIZE / PARSONS（白名单
 * 正则校验，阶段合法性由 graph 状态机权威判定）。eventData 按事件携带：IDEATING.thought_text、
 * CHAT.message、ERROR_FEEDBACK/AC_REVIEW.submission_id、EXECUTION_TRACE/VISUALIZE.code、
 * VISUALIZE.intent、PARSONS.sub_skill。
 */
public record TutorWorkflowRunRequest(
    @NotNull Long problemId,
    @NotBlank
        @Pattern(
            regexp =
                "READING|IDEATING|SKELETON|CODING|ERROR_FEEDBACK|AC_REVIEW|TRANSFER|CHAT"
                    + "|KNOWLEDGE_REVIEW|EXECUTION_TRACE|VISUALIZE|PARSONS",
            message = "未知的工作流事件")
        String event,
    Map<String, Object> eventData) {

  public Map<String, Object> eventDataOrEmpty() {
    return eventData == null ? Map.of() : eventData;
  }
}
