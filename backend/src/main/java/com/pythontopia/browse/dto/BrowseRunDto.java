package com.pythontopia.browse.dto;

import com.pythontopia.browse.entity.BrowseRunEntity;
import com.pythontopia.browse.entity.BrowseRunStatus;
import java.time.OffsetDateTime;

/**
 * 会话头视图（{@code POST /api/browse/runs} 返回）。不含步骤/引用明细——明细见
 * {@link BrowseRunDetailDto}（{@code GET /api/browse/runs/{id}}）。{@code error} 仅 FAILED 时非空。
 */
public record BrowseRunDto(
    long id,
    String goal,
    BrowseRunStatus status,
    String finalSummary,
    boolean truncated,
    String error,
    OffsetDateTime createdAt) {

  public static BrowseRunDto from(BrowseRunEntity entity) {
    return new BrowseRunDto(
        entity.id(),
        entity.goal(),
        entity.status(),
        entity.finalSummary(),
        entity.truncated(),
        entity.error(),
        entity.createdAt());
  }
}
