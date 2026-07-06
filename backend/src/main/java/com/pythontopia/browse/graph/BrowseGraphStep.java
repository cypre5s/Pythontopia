package com.pythontopia.browse.graph;

/**
 * graph BROWSE 返回的单步（对齐 {@code tools/browse/contract.py:BrowseStep}）。{@code action} ∈
 * {navigate, read, search, stop}；{@code url} 为该步命中地址（search/stop 可空串）；{@code summary}
 * 为去敏后的可读摘要。
 */
public record BrowseGraphStep(int index, String action, String url, String summary) {}
