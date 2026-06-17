package com.pythontopia.browse.entity;

/**
 * {@code browse_step} 行的领域表示（列名/语义见 V170 迁移）。{@code idx} 为 0-based 有界序号，
 * {@code url} 为该步命中的外部地址（search/stop 步可能为空串），{@code summary} 为去敏后的可读摘要。
 */
public record BrowseStepEntity(
    Long id, Long runId, int idx, String action, String url, String summary) {}
