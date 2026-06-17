package com.pythontopia.loop;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code loop_run} 行的领域投影（V171）。镜像 graph 侧 {@code schemas/agentic_loop.py} 的
 * {@code AgenticLoopRunRecord} 关键列，外加 DB 生成的 {@code id} / {@code createdAt}。不可变记录，
 * 由 {@link LoopRunRepository} 读写。
 */
public record LoopRunEntity(
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
    OffsetDateTime createdAt) {}
