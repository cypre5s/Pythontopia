package com.pythontopia.learner.recommendation;

import java.util.List;

/**
 * 一条「今日推送」。{@code reason} 为推荐理由（孪生驱动时引用学情洞察，如「因近期在 X 反复出错」），
 * {@code evidenceEventIds} 指向支撑该理由的 {@code learning_event_log.id}，让推送可溯源（P3.3）；
 * 非洞察驱动的推荐 {@code evidenceEventIds} 为空列表。
 */
public record RecommendationDto(
    Long resourceId,
    String title,
    String type,
    String kcCode,
    String kcName,
    String reason,
    List<Long> evidenceEventIds) {}
