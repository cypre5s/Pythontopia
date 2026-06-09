package com.pythontopia.learner.memory;

import java.time.Instant;
import java.util.Set;

/**
 * 记忆流检索的确定性打分纯函数（零依赖、零 LLM），借鉴 Stanford Generative Agents 的
 * {@code recency × importance × relevance} 综合分。
 *
 * <ul>
 *   <li>recency = {@code 0.5^(ageDays / 14)}（半衰期 14 天，对齐画像 {@code DECAY_HALF_LIFE_DAYS}）。
 *   <li>importance = {@code importance / 5}。
 *   <li>relevance：focusKc 命中 → 1.0；同章 KC → 0.5；否则 0；无聚焦（focusKc 空）恒 0。
 *   <li>total = {@code 0.4*recency + 0.3*importance + 0.3*relevance}。
 * </ul>
 */
public final class MemoryScorer {

  private static final double RECENCY_WEIGHT = 0.4;
  private static final double IMPORTANCE_WEIGHT = 0.3;
  private static final double RELEVANCE_WEIGHT = 0.3;
  private static final double RECENCY_HALF_LIFE_DAYS = 14.0;
  private static final double MAX_IMPORTANCE = 5.0;

  private MemoryScorer() {}

  /** recency/importance/relevance 与综合分的分解结果（供前端透明展示与排序）。 */
  public record ScoreBreakdown(double recency, double importance, double relevance, double total) {}

  /** 无同章上下文的便捷重载：relevance 仅做精确命中（1.0）或 0。 */
  public static ScoreBreakdown score(MemoryRow row, String focusKc, Instant now) {
    return score(row, focusKc, Set.of(), now);
  }

  /**
   * 完整打分（纯 KC 相关性，无语义向量）。
   *
   * @param sameChapterKcCodes focusKc 所在章节的全部 KC code（含自身），用于「同章 → 0.5」判定；空集表示无同章加权。
   */
  public static ScoreBreakdown score(
      MemoryRow row, String focusKc, Set<String> sameChapterKcCodes, Instant now) {
    return score(row, focusKc, sameChapterKcCodes, 0.0, now);
  }

  /**
   * 完整打分（语义增强）：{@code relevance = max(KC 匹配分, clamp01(semanticSim))}，recency/importance
   * 权重与公式不变（0.4/0.3/0.3）。
   *
   * @param sameChapterKcCodes focusKc 所在章节的全部 KC code（含自身），用于「同章 → 0.5」判定；空集表示无同章加权。
   * @param semanticSim 查询向量与该记忆向量的余弦相似度（建议 0..1，越界自动裁剪到 [0,1]）；无向量/离线传
   *     {@code 0} 即退化为纯 KC 匹配，向后兼容现状（零回归）。
   */
  public static ScoreBreakdown score(
      MemoryRow row,
      String focusKc,
      Set<String> sameChapterKcCodes,
      double semanticSim,
      Instant now) {
    double recency = recency(row.createdAt(), now);
    double importance = clamp01(row.importance() / MAX_IMPORTANCE);
    double kcRelevance = relevance(row.kcCodes(), focusKc, sameChapterKcCodes);
    double relevance = Math.max(kcRelevance, clamp01(semanticSim));
    double total =
        RECENCY_WEIGHT * recency + IMPORTANCE_WEIGHT * importance + RELEVANCE_WEIGHT * relevance;
    return new ScoreBreakdown(recency, importance, relevance, total);
  }

  private static double recency(Instant createdAt, Instant now) {
    if (createdAt == null || now == null) {
      return 0.0;
    }
    double ageDays = (now.toEpochMilli() - createdAt.toEpochMilli()) / 86_400_000.0;
    if (ageDays <= 0) {
      return 1.0;
    }
    return Math.pow(0.5, ageDays / RECENCY_HALF_LIFE_DAYS);
  }

  private static double relevance(
      java.util.List<String> kcCodes, String focusKc, Set<String> sameChapterKcCodes) {
    if (focusKc == null || focusKc.isBlank() || kcCodes == null || kcCodes.isEmpty()) {
      return 0.0;
    }
    if (kcCodes.contains(focusKc)) {
      return 1.0;
    }
    if (sameChapterKcCodes != null && !sameChapterKcCodes.isEmpty()) {
      for (String code : kcCodes) {
        if (sameChapterKcCodes.contains(code)) {
          return 0.5;
        }
      }
    }
    return 0.0;
  }

  private static double clamp01(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }
}
