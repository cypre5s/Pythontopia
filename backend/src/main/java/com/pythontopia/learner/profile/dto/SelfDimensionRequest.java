package com.pythontopia.learner.profile.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 学生 / 教师对单个维度的自评 / 直写请求。
 *
 * <p>所有字段均可空；至少需要提供 scoreDelta、tagsAdd 或 metadata 之一才会产生有效变更。
 *
 * <p>{@code evidenceIncrement} 必须落在 [0,5]：一次自评最多只算 5 条证据。放开上界等于让学生一次请求把
 * confidence 顶到 1.0，用自编 tag 绕过 profile_brief 的置信度门槛注入下游 prompt；放开下界（负值）则会把
 * evidence_count 打成负数，使该生画像对全部下游永久静默。
 */
public record SelfDimensionRequest(
    @DecimalMin("-1.0") @DecimalMax("1.0") Double scoreDelta,
    @Min(0) @Max(5) Integer evidenceIncrement,
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
