package com.pythontopia.learner.profile.dto;

import java.util.List;
import java.util.Map;

/**
 * 八维画像维度增量。空字段表示该子操作跳过。
 *
 * <p>语义：
 *
 * <ul>
 *   <li>{@code scoreDelta}：在原有 score 上累加，最终结果被 clamp 到 [0,1]。
 *   <li>{@code evidenceIncrement}：在 evidence_count 上累加。
 *   <li>{@code tagsAdd}：去重追加到 tags 数组。
 *   <li>{@code metadata}：直接覆盖写入 dimension 子对象的对应键（如 weak_kcs、kc_mastery_avg）。
 *   <li>{@code source}：审计来源，期望来自 profile_revision.source 枚举；不在枚举内时跳过 revision 写入但仍执行
 *       dimension 更新。
 * </ul>
 */
public record DimensionDelta(
    Double scoreDelta,
    Integer evidenceIncrement,
    List<String> tagsAdd,
    Map<String, Object> metadata,
    String source) {

  public static DimensionDelta ofScore(double scoreDelta, String source) {
    return new DimensionDelta(scoreDelta, null, null, null, source);
  }

  public static DimensionDelta ofScoreAndEvidence(double scoreDelta, int evidence, String source) {
    return new DimensionDelta(scoreDelta, evidence, null, null, source);
  }

  public static DimensionDelta ofTags(double scoreDelta, List<String> tags, String source) {
    return new DimensionDelta(scoreDelta, null, tags, null, source);
  }
}
