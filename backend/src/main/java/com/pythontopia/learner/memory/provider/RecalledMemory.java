package com.pythontopia.learner.memory.provider;

import java.time.Instant;

/**
 * 一条召回记忆（provider 无关）。
 *
 * @param text 可读记忆文本（本地为中文摘要 summaryZh；外部为 provider 返回的 memory/fact 文本）
 * @param kcCode 关联知识点 code，可空（外部记忆未必带 KC）
 * @param kind 记忆/事件类别（本地为 eventType，如 {@code practice_failed}；外部为 metadata.kind 或 {@code fact}）
 * @param occurredAt 记忆发生 / 写入时间，可空；用于 brief 的时序排序与 as_of
 * @param score 相关性 / 综合分（高→优先），用于排序
 * @param source 来源 provider（{@code local} / {@code mem0} / {@code zep}）
 */
public record RecalledMemory(
    String text, String kcCode, String kind, Instant occurredAt, double score, String source) {

  /**
   * 是否为「受阻 / 失败 / 薄弱」类记忆（用于 brief 聚合 weak_kcs）。覆盖本地事件类型（{@code
   * practice_failed} / {@code practice_abandoned} / lapse）与外部 provider 标注的薄弱记忆（{@code weak_kc}）。
   */
  public boolean isStruggle() {
    if (kind == null) {
      return false;
    }
    String lower = kind.toLowerCase(java.util.Locale.ROOT);
    return lower.contains("fail")
        || lower.contains("abandon")
        || lower.contains("lapse")
        || lower.contains("weak");
  }
}
