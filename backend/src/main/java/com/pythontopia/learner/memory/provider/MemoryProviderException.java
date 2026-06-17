package com.pythontopia.learner.memory.provider;

/**
 * 外部记忆 provider（Mem0 / Zep）故障信号：HTTP 不可达 / 超时 / 5xx / 响应不可解析 / 配置缺失。
 *
 * <p>由 {@link MemoryProviderRouter} 捕获并 <strong>fail-soft 回退 {@link LocalMemoryProvider}</strong>，
 * 不向上层（brief / 闭环主链路）抛出，符合 §0「观测/外部依赖 fail-soft」红线。
 */
public class MemoryProviderException extends RuntimeException {

  public MemoryProviderException(String message) {
    super(message);
  }

  public MemoryProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
