package com.pythontopia.loop.dto;

import com.pythontopia.loop.LoopRunEntity;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 闭环 run 审计详情响应体（{@code POST /api/loop/runs} 落库回执 + {@code GET /api/loop/runs/{id}}）。
 * 完整镜像 {@code AgenticLoopRunRecord} 字段，并补 DB 生成的 {@code id} / {@code createdAt}，供前端
 * 审计视图与 graph↔backend 对账使用。
 */
public record LoopRunDto(
    Long id,
    Long userId,
    String sessionId,
    int turn,
    int maxSteps,
    int stepsUsed,
    int replanCount,
    List<String> toolsUsed,
    boolean groundingPass,
    boolean safetyPass,
    int inputTokens,
    int outputTokens,
    String traceId,
    OffsetDateTime createdAt) {

  public static LoopRunDto from(LoopRunEntity entity) {
    return new LoopRunDto(
        entity.id(),
        entity.userId(),
        entity.sessionId(),
        entity.turn(),
        entity.maxSteps(),
        entity.stepsUsed(),
        entity.replanCount(),
        entity.toolsUsed() == null ? List.of() : List.copyOf(entity.toolsUsed()),
        entity.groundingPass(),
        entity.safetyPass(),
        entity.inputTokens(),
        entity.outputTokens(),
        entity.traceId(),
        entity.createdAt());
  }
}
