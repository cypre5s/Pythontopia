package com.pythontopia.mainline.domain;

/**
 * 章末大测试状态机（对齐 V157 chapter_test.status CHECK 约束 + r15 计划 §11.1）。
 *
 * <pre>
 * PENDING ─prepare─▶ GENERATING ─graph ok─▶ READY ─start─▶ IN_PROGRESS ─submit─▶ SUBMITTED
 *                        │                     ▲                              └─overtime─▶ EXPIRED
 *                        └─graph fail(≤2 retry)─┴─golden 兜底──┘
 *                        └─all fail & no golden─▶ FAILED
 * </pre>
 */
public enum ChapterTestStatus {
  PENDING,
  GENERATING,
  READY,
  IN_PROGRESS,
  SUBMITTED,
  EXPIRED,
  FAILED
}
