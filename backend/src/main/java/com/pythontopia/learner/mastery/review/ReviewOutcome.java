package com.pythontopia.learner.mastery.review;

/** 一次"复习"的结果，决定 FSRS-lite 稳定度如何演化。 */
public enum ReviewOutcome {
  /** 成功召回（练习通过 / 调试修复）：稳定度增长，低可提取性时增长更多。 */
  SUCCESS,
  /** 召回失败（练习未过）：稳定度回落、lapse 计数 +1。 */
  LAPSE,
  /** 重学（阅读课件 / 资源消费）：刷新复习计时，温和增长，不计入成功召回。 */
  RESTUDY
}
