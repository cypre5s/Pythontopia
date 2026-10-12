package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 前端请求 boss 题目的 body；画像字段供 PracticeAgent + ProfileAgent 按学生画像偏好出题。
 *
 * <p>stage 1..4；chapter 由 path variable 提供，不在 body。
 *
 * @param stage 当前阶段 1..4
 * @param kcCode 锚定 KC（兼容旧字段，等价于 preferredKc 的旧名）
 * @param difficulty 期望难度 1..5（null 由后端按 stage 推断）
 * @param preferredKc 偏好锚定 KC（覆盖 kcCode；前端 ArenaPage 关卡传）
 * @param preferredKind 关卡题型（fill_blank/debug/multi_round 等），用于 LLM 模板选择
 * @param profileSignals 学生画像信号，{@link ProfileSignalsPayload}
 * @param arenaLevel 闯关塔单关 id（如 "1-3"）：预生成 / 复用题的维度之一（endless / arena 传）
 * @param expeditionRunId 当前远征 run id；只有服务端当前活跃 run 可关联奖励
 */
public record BossProblemRequest(
    @Min(1) @Max(4) @JsonProperty("stage") int stage,
    @JsonProperty("kcCode") @Size(max = 64) String kcCode,
    @Min(1) @Max(5) @JsonProperty("difficulty") Integer difficulty,
    @JsonProperty("preferredKc") @Size(max = 64) String preferredKc,
    @JsonProperty("preferredKind") @Size(max = 32) String preferredKind,
    @JsonProperty("profileSignals") ProfileSignalsPayload profileSignals,
    @JsonProperty("arenaLevel") @Size(max = 16) String arenaLevel,
    @JsonProperty("expeditionRunId")
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String expeditionRunId) {

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
    if (arenaLevel != null && arenaLevel.isBlank()) {
      arenaLevel = null;
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
