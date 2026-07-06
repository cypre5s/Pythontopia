package com.pythontopia.mainline.domain;

import java.time.OffsetDateTime;
import java.util.List;

/** mainline_stage_progress 投影：某学生在某 stage 的三件套（看/学/练）打点。 */
public record MainlineStageProgress(
    long id,
    long userId,
    long stageId,
    int videoWatchedSec,
    List<Long> pagesRead,
    List<Long> examplesDone,
    List<Long> questionsCorrect,
    OffsetDateTime completedAt,
    OffsetDateTime updatedAt) {

  public boolean completed() {
    return completedAt != null;
  }
}
