package com.pythontopia.browse.dto;

import com.pythontopia.browse.entity.BrowseRunEntity;
import com.pythontopia.browse.entity.BrowseRunStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会话明细视图（{@code GET /api/browse/runs/{id}}）：会话头 + 有界步骤时间线 + 外部命中引用。
 * {@code citations} 全部为「外部内容，非课件」标注（{@link BrowseCitationDto}）。
 */
public record BrowseRunDetailDto(
    long id,
    String goal,
    BrowseRunStatus status,
    String finalSummary,
    boolean truncated,
    String error,
    OffsetDateTime createdAt,
    List<BrowseStepDto> steps,
    List<BrowseCitationDto> citations) {

  public static BrowseRunDetailDto from(
      BrowseRunEntity run, List<BrowseStepDto> steps, List<BrowseCitationDto> citations) {
    return new BrowseRunDetailDto(
        run.id(),
        run.goal(),
        run.status(),
        run.finalSummary(),
        run.truncated(),
        run.error(),
        run.createdAt(),
        List.copyOf(steps),
        List.copyOf(citations));
  }
}
