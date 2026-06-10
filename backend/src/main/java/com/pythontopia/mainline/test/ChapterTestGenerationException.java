package com.pythontopia.mainline.test;

/**
 * 章测题面生成失败（graph 不可达 / 返回非法 / 自检不过）。fail-fast 语义：由
 * {@link ChapterTestPregenService} 捕获后落 FAILED 并按 §4.1 走重试/golden 兜底，绝不静默降级成烂题。
 */
public class ChapterTestGenerationException extends RuntimeException {

  public ChapterTestGenerationException(String message) {
    super(message);
  }

  public ChapterTestGenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}
