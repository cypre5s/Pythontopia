package com.pythontopia.learner.quest.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Evidence payload supplied when learners click "提交完成" in the board / 公告板.
 *
 * <p>当 quest.required_evidence_type 为非空时，QuestService 必须在 {@code complete()} 入口里校验
 * 对应字段是否存在并合法；否则保持旧行为（兼容 PATH_PLANNER / SYSTEM_DEFAULT 历史任务）。
 */
public record QuestCompleteRequest(
    @Size(max = 256) String note,
    @Positive Long submissionId,
    @Positive Long resourceId,
    @Positive Long pageReadId,
    @DecimalMin("0.00") @DecimalMax("1.00") Double kcMasteryDelta) {

  public QuestCompleteRequest {
    if (note != null && note.isBlank()) {
      note = null;
    }
  }

  public QuestCompleteRequest(String note) {
    this(note, null, null, null, null);
  }
}
