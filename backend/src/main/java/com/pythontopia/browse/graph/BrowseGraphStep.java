package com.pythontopia.browse.graph;

/**
 * graph BROWSE 返回的单步（对齐 {@code tools/browse/contract.py:BrowseStep}）。{@code action} ∈
 * {navigate, read, search, stop}；{@code url} 为该步命中地址（search/stop 可空串）；{@code summary}
 * 为兼容旧端的去敏摘要；{@code title}/{@code snippet} 是可独立展示和持久化的结构化网页证据。
 */
public record BrowseGraphStep(
    int index, String action, String url, String summary, String title, String snippet) {}
