package com.pythontopia.tutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

/**
 * 导学工作流事件请求（前端 → Java）。
 *
 * <p>阶段卡片与辅助事件由 graph 状态机校验；Tutor 事件也包含计划推荐/开始/响应/控制，以及聊天
 * 反馈与上下文压缩。事件数据由 graph 的逐事件 schema 再校验。
 */
public record TutorWorkflowRunRequest(
    @NotNull Long problemId,
    @NotBlank
        @Pattern(
            regexp =
                "READING|IDEATING|SKELETON|CODING|ERROR_FEEDBACK|AC_REVIEW|TRANSFER|CHAT"
                    + "|KNOWLEDGE_REVIEW|EXECUTION_TRACE|VISUALIZE|PARSONS|AGENT_FEEDBACK"
                    + "|COMPACT|PLAN_RECOMMEND|PLAN_START|PLAN_RESPONSE|PLAN_STEERING",
            message = "未知的工作流事件")
        String event,
    Map<String, Object> eventData) {

  public Map<String, Object> eventDataOrEmpty() {
    return eventData == null ? Map.of() : eventData;
  }
}
