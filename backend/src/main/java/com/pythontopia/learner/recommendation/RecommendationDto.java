package com.pythontopia.learner.recommendation;

import java.util.List;

/**
 * 一条「今日推送」。{@code reason} 为推荐理由（孪生驱动时引用学情洞察，如「因近期在 X 反复出错」），
 * {@code evidenceEventIds}/{@code checkIds}/{@code pageIds} 指向支撑该理由的事件、检查和课件页证据；
 * 非证据驱动的推荐证据列表为空。
 */
public record RecommendationDto(
    Long resourceId,
    String title,
    String type,
    String kcCode,
    String kcName,
    String reason,
    List<Long> evidenceEventIds,
    List<Long> checkIds,
    List<Long> pageIds) {

  public RecommendationDto(
      Long resourceId,
      String title,
      String type,
      String kcCode,
      String kcName,
      String reason,
      List<Long> evidenceEventIds) {
    this(resourceId, title, type, kcCode, kcName, reason, evidenceEventIds, List.of(), List.of());
  }

  public RecommendationDto {
    evidenceEventIds = evidenceEventIds == null ? List.of() : List.copyOf(evidenceEventIds);
    checkIds = checkIds == null ? List.of() : List.copyOf(checkIds);
    pageIds = pageIds == null ? List.of() : List.copyOf(pageIds);
  }
}
