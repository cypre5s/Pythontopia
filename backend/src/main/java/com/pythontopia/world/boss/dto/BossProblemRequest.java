package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 前端请求 boss 题目时 body。
 *
 * <p>R-13 §-1 续：扩展 preferredKc / preferredKind / profileSignals 让 PracticeAgent +
 * ProfileAgent 基于学生画像偏好出题。
 *
 * <p>stage 1..4；chapter 由 path variable 提供，不在 body。
 *
 * @param stage 当前阶段 1..4
 * @param kcCode 锚定 KC（兼容旧字段，等价于 preferredKc 的旧名）
 * @param difficulty 期望难度 1..5（null 由后端按 stage 推断）
 * @param preferredKc 偏好锚定 KC（覆盖 kcCode；前端 ArenaPage 关卡传）
 * @param preferredKind 关卡题型（fill_blank/debug/multi_round 等），用于 LLM 模板选择
 * @param profileSignals 学生画像信号，{@link ProfileSignalsPayload}
 */
public record BossProblemRequest(
    @Min(1) @Max(4) @JsonProperty("stage") int stage,
    @JsonProperty("kcCode") String kcCode,
    @JsonProperty("difficulty") Integer difficulty,
    @JsonProperty("preferredKc") String preferredKc,
    @JsonProperty("preferredKind") String preferredKind,
    @JsonProperty("profileSignals") ProfileSignalsPayload profileSignals) {

  public BossProblemRequest {
    if (kcCode != null && kcCode.isBlank()) {
      kcCode = null;
    }
    if (preferredKc != null && preferredKc.isBlank()) {
      preferredKc = null;
    }
    if (preferredKind != null && preferredKind.isBlank()) {
      preferredKind = null;
    }
  }

  /** 学生画像信号 payload（与前端 ProfileSignalsDto 形状一致）。 */
  public record ProfileSignalsPayload(
      @JsonProperty("weakKcCodes") List<String> weakKcCodes,
      @JsonProperty("pythonSelfRatedLevel") Integer pythonSelfRatedLevel,
      @JsonProperty("successRate") Double successRate,
      @JsonProperty("mediaPriority") List<String> mediaPriority,
      @JsonProperty("resilience") Double resilience,
      @JsonProperty("questPreferredKind") String questPreferredKind) {}
}
