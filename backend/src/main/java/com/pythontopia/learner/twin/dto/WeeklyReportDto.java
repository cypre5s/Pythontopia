package com.pythontopia.learner.twin.dto;

import java.util.List;

/**
 * 数字孪生周报：聚合包含今天在内的最近七个自然日的记忆流要点、反思洞察、掌握度变化与 AI
 * 团队为你生成过的资源摘要，供前端「本周你的 AI
 * 团队」时间线展示。所有字段仅包含当前学生本人的数据。
 */
public record WeeklyReportDto(
    String periodStart,
    String periodEnd,
    MemoryBrief memoryBrief,
    List<InsightBrief> insights,
    List<MasteryDeltaBrief> masteryDeltas,
    List<ResourceRunBrief> resourceRuns) {

  /** 记忆流摘要。 */
  public record MemoryBrief(
      int totalEvents,
      int failedPractices,
      int succeededPractices,
      List<String> topKcCodes,
      List<String> highlights) {}

  /** 单条洞察简版（前 5 条，超出省略）。 */
  public record InsightBrief(
      long id,
      String text,
      String dimensionKey,
      List<String> tags,
      String createdAt) {}

  /** 当前掌握度与近期可信练习/微测/调试事件的实际应用贡献（不含衰减与人工调整）。 */
  public record MasteryDeltaBrief(
      String kcCode,
      Double currentScore,
      Double confidence,
      Double weeklyDelta,
      int evidenceCount,
      List<String> evidenceSources) {}

  /** AI 团队为你做了什么（资源运行摘要）。 */
  public record ResourceRunBrief(
      String agentName,
      String scenario,
      String status,
      int evidencePageCount,
      String createdAt) {}
}
