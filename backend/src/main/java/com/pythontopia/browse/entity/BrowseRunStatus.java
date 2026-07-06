package com.pythontopia.browse.entity;

/**
 * 联网查证会话状态，与 {@code browse_run.status} CHECK 约束（V170）一一对应。
 *
 * <ul>
 *   <li>{@link #PENDING} 受理中（同步落库下一般不出现，保留作前向兼容）。
 *   <li>{@link #SUCCESS} 正常完成。
 *   <li>{@link #TRUNCATED} 触达有界步数上限被截断（对应 {@code BrowseResult.truncated}）。
 *   <li>{@link #FAILED} graph 内 fail-fast（{@code BrowseResult.error} 非空，已走步骤仍保留）。
 * </ul>
 */
public enum BrowseRunStatus {
  PENDING,
  SUCCESS,
  TRUNCATED,
  FAILED
}
