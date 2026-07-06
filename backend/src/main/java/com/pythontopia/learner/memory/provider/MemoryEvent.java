package com.pythontopia.learner.memory.provider;

import java.time.Instant;

/**
 * 待写入记忆的输入事件。
 *
 * @param kind 记忆类别（如 {@code fact} / {@code preference} / {@code weak_kc}）；空白时由实现兜底为 {@code fact}
 * @param text 记忆文本（必填，空白则 {@link MemoryProvider#remember} 视为非法）
 * @param kcCode 关联 KC，可空
 * @param occurredAt 发生时间，可空（默认 now）
 */
public record MemoryEvent(String kind, String text, String kcCode, Instant occurredAt) {

  public MemoryEvent {
    kind = (kind == null || kind.isBlank()) ? "fact" : kind.trim();
  }

  /** 便捷构造：只有文本 + 类别，时间默认 now。 */
  public static MemoryEvent of(String kind, String text, String kcCode) {
    return new MemoryEvent(kind, text, kcCode, Instant.now());
  }

  public boolean isBlank() {
    return text == null || text.isBlank();
  }
}
