package com.pythontopia.mainline.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 关卡地图总览（{@code GET /api/mainline/overview} 响应，对齐 r15 计划 §9）。
 *
 * <p>由单次聚合（少量按 user 一次取齐的查询，§11.4 avoid N+1）在内存拼装。
 */
public record MainlineOverviewDto(int totalStars, List<ChapterOverview> chapters) {

  public record ChapterOverview(
      int chapter,
      String title,
      boolean unlocked,
      int stars,
      Integer bestTestScore,
      List<StageOverview> stages,
      String testStatus,
      JsonNode starBreakdown,
      RecoveryDto recovery) {}

  public record StageOverview(
      long id,
      int index,
      String title,
      List<String> kcCodes,
      int videoWatchedSec,
      int requiredVideoSec,
      int pagesRead,
      int requiredPages,
      int examplesDone,
      int requiredExamples,
      boolean completed) {}

  /** 补星进度（Wave 2 B1 接线；当前由 chapter_star.breakdown.recovered 推导，默认 false）。 */
  public record RecoveryDto(boolean errorbookDone, boolean endlessDone) {}
}
