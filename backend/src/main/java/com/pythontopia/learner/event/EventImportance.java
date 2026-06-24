package com.pythontopia.learner.event;

import java.util.Map;

/**
 * 记忆流（Grounded Generative Agents）重要度规则：把规范化 snake_case {@code event_type} 映射到
 * 确定性重要度 1–5（写入即定、零 LLM），供 {@code recency × importance × relevance} 检索与反思优先级使用。
 *
 * <p>越高越值得被反思/检索：逆袭与调试修复（5）> 失败/放弃（4）> 通过（3）> 讲法采纳/探针/重试/初始化（2）
 * > 资源/多模态消费（1）。未命中类型兜底为 1。
 */
public final class EventImportance {

  /** 未知 / 未登记 event_type 的兜底重要度。 */
  public static final int DEFAULT_IMPORTANCE = 1;

  private static final Map<String, Integer> SCORES =
      Map.ofEntries(
          Map.entry("taught_ai_succeeded", 5),
          Map.entry("comeback_solved", 5),
          Map.entry("debug_resolved", 5),
          Map.entry("practice_failed", 4),
          Map.entry("resource_check_failed", 4),
          Map.entry("practice_abandoned", 4),
          Map.entry("taught_ai_failed", 3),
          Map.entry("resource_check_passed", 3),
          Map.entry("practice_succeeded", 3),
          Map.entry("tutor_style_engaged", 2),
          Map.entry("probe_choice_made", 2),
          Map.entry("practice_retried", 2),
          Map.entry("profile_bootstrapped", 2),
          Map.entry("resource_consumed", 1),
          Map.entry("media_consumed", 1));

  private EventImportance() {}

  /** 返回该 event_type 的重要度（1–5）；null / 空 / 未登记类型返回 {@link #DEFAULT_IMPORTANCE}。 */
  public static int of(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return DEFAULT_IMPORTANCE;
    }
    return SCORES.getOrDefault(eventType, DEFAULT_IMPORTANCE);
  }
}
