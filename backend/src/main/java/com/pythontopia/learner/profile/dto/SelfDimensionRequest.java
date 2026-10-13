package com.pythontopia.learner.profile.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 学生 / 教师对单个维度的自评 / 直写请求。
 *
 * <p>自评只能修改主观分数与标签；可信证据和结构化 metadata 仅由服务端学习事件写入。
 */
public record SelfDimensionRequest(
    @DecimalMin("-1.0") @DecimalMax("1.0") Double scoreDelta,
    @Size(max = 16) List<@Size(max = 64) String> tagsAdd) {

  /** 转成内部 DimensionDelta，source 由调用方决定。 */
  public DimensionDelta toDelta(String source) {
    return new DimensionDelta(scoreDelta, null, tagsAdd, null, source);
  }

  /** 防御性：返回是否为空请求（避免空 PUT 产生无意义 revision）。 */
  public boolean isEmpty() {
    return scoreDelta == null && (tagsAdd == null || tagsAdd.isEmpty());
  }
}
