package com.pythontopia.browse.entity;

import java.time.OffsetDateTime;

/**
 * {@code browse_run} 行的领域表示（列名/语义见 V170 迁移）。{@code error} 仅在
 * {@link BrowseRunStatus#FAILED} 时非空；外部命中 url 不在此表，按步骤（{@link BrowseStepEntity}）记录。
 */
public record BrowseRunEntity(
    Long id,
    Long userId,
    String goal,
    BrowseRunStatus status,
    String finalSummary,
    boolean truncated,
    String error,
    OffsetDateTime createdAt) {}
