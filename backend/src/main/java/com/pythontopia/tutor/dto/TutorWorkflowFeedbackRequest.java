package com.pythontopia.tutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** 学生对导学工作流动作/卡片的轻量反馈，用于 coach 行为事件沉淀。 */
public record TutorWorkflowFeedbackRequest(
    @NotBlank
        @Pattern(
            regexp = "ACTION_CLICKED|CHECKPOINT_COMPLETED|CARD_HELPFUL|CARD_NOT_HELPFUL",
            message = "未知的导学反馈事件")
        String eventType,
    @Size(max = 64) String actionKey,
    @Size(max = 64) String cardKey,
    Map<String, Object> payload) {

  public Map<String, Object> payloadOrEmpty() {
    return payload == null ? Map.of() : payload;
  }
}
