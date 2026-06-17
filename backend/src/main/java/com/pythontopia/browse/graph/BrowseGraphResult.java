package com.pythontopia.browse.graph;

import java.util.List;

/**
 * graph BROWSE 执行结果的领域表示（对齐 {@code tools/browse/contract.py:BrowseResult}）。
 *
 * <p>注意区分两种「失败」：
 *
 * <ul>
 *   <li>传输失败（graph 不可达 / HTTP 错误 / 空响应）：由 {@link BrowseGraphClient} 抛
 *       {@code BusinessException(502)}，不会产出本结果。
 *   <li>业务 fail-fast：graph 正常返回（HTTP 200）但 {@link #error()} 非空，且 {@link #steps()} 仍带已走
 *       部分（§8.5）。此时落库为 {@code FAILED} 并照常返回会话，不抛异常。
 * </ul>
 *
 * <p>{@link #citations()} 是命中 url，统一作为「外部内容，非课件」呈现，绝不进课件证据链。
 */
public record BrowseGraphResult(
    List<BrowseGraphStep> steps,
    String finalSummary,
    List<String> citations,
    boolean truncated,
    String error) {

  public BrowseGraphResult {
    steps = steps == null ? List.of() : List.copyOf(steps);
    citations = citations == null ? List.of() : List.copyOf(citations);
    finalSummary = finalSummary == null ? "" : finalSummary;
    error = error == null ? "" : error;
  }
}
