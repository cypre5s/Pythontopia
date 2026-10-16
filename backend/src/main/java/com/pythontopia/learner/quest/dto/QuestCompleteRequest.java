package com.pythontopia.learner.quest.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 学生在公告板点击"提交完成"时附带的证据载荷。
 *
 * <p>当 quest.required_evidence_type 非空时，QuestService 须在 {@code complete()} 校验对应字段存在且合法；
 * 为 null 时保持旧行为（兼容 PATH_PLANNER / SYSTEM_DEFAULT 历史任务）。
 * kcMasteryDelta 仅保留为客户端证据声明；权威增量从服务端掌握度事件读取。
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
