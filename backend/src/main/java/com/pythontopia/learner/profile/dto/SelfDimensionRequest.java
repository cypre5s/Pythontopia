package com.pythontopia.learner.profile.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 学生 / 教师对单个维度的自评 / 直写请求。
 *
 * <p>所有字段均可空；至少需要提供 scoreDelta、tagsAdd 或 metadata 之一才会产生有效变更。
 */
public record SelfDimensionRequest(
    @DecimalMin("-1.0") @DecimalMax("1.0") Double scoreDelta,
    Integer evidenceIncrement,
    @Size(max = 16) List<@Size(max = 64) String> tagsAdd,
    Map<String, Object> metadata) {

  /** 转成内部 DimensionDelta，source 由调用方决定。 */
  public DimensionDelta toDelta(String source) {
    return new DimensionDelta(scoreDelta, evidenceIncrement, tagsAdd, metadata, source);
  }

  /** 防御性：返回是否为空请求（避免空 PUT 产生无意义 revision）。 */
  public boolean isEmpty() {
    return scoreDelta == null
        && evidenceIncrement == null
        && (tagsAdd == null || tagsAdd.isEmpty())
        && (metadata == null || metadata.isEmpty());
  }
}
