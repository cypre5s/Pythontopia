package com.pythontopia.learner.mastery.review;

import org.springframework.stereotype.Component;

/**
 * FSRS-lite（DSR 记忆模型）间隔复习计算器：纯函数、确定性、可单测，不触库不调 LLM。
 *
 * <p>三要素：难度 D、稳定度 S（可提取性 R 从 1.0 衰减到锚点 0.9 所需天数）、可提取性 R。
 * 采用幂律遗忘曲线 {@code R(t) = (1 + FACTOR * t / S) ^ (-DECAY)}，其中 FACTOR 选取使
 * {@code R(S) = 0.9} 恒成立。成功召回时 S 增大，且"越接近遗忘（R 越低）时成功召回，S 增长越多"
 * （间隔效应）；失败回落 S；难度向基线均值回归以避免"难度地狱"。
 */
@Component
public class ReviewScheduler {

  /** 幂律衰减指数（FSRS-5 默认量级）。 */
  public static final double DECAY = 0.5;

  /** 使 R(S)=0.9 的系数：FACTOR = 0.9^(-1/DECAY) - 1 ≈ 0.2346。 */
  public static final double FACTOR = Math.pow(0.9, -1.0 / DECAY) - 1.0;

  /** 默认目标留存率（到期阈值）。Phase 2 可按画像（情绪/坚持度、基础）个性化。 */
  public static final double DEFAULT_TARGET_RETENTION = 0.9;

  static final double MIN_STABILITY = 0.5;
  static final double MAX_STABILITY = 365.0;
  static final double DIFFICULTY_BASELINE = 0.30;

  // 成功召回增长系数：S_new = S * (1 + W_SUCCESS * (1 - R) * (1 - 0.3 * difficulty))。
  private static final double W_SUCCESS = 2.0;
  // 失败回落系数与难度惩罚。
  private static final double LAPSE_MULT = 0.4;
  private static final double LAPSE_DIFFICULTY_UP = 0.15;
  // 重学（看课件）温和增长。
  private static final double RESTUDY_MULT = 1.15;
  // 难度均值回归速度（成功时）。
  private static final double DIFFICULTY_REVERSION = 0.10;
  // 无既有状态时的首次播种稳定度（天）。
  private static final double SEED_SUCCESS = 3.0;
  private static final double SEED_RESTUDY = 1.5;
  private static final double SEED_LAPSE = 1.0;

  /**
   * 无既有复习状态时，从掌握度播种初始稳定度（天）。与 V133 迁移回填公式一致：
   * {@code clamp(1 + 13 * mastery, 0.5, 14)}，承接旧的 14 天半衰期口径。
   */
  public double seedStability(double masteryScore) {
    double m = Math.max(0.0, Math.min(1.0, masteryScore));
    return Math.max(MIN_STABILITY, Math.min(14.0, 1.0 + 13.0 * m));
  }

  /** 给定稳定度与已过天数，返回当前可提取性 R ∈ [0,1]。 */
  public double retrievability(double stability, double elapsedDays) {
    double s = Math.max(MIN_STABILITY, stability);
    double t = Math.max(0.0, elapsedDays);
    double r = Math.pow(1.0 + FACTOR * t / s, -DECAY);
    return clampUnit(r);
  }

  /** 达到目标留存率所需的复习间隔（天）；target=0.9 时恰为 S。 */
  public double intervalDays(double stability, double targetRetention) {
    double s = Math.max(MIN_STABILITY, stability);
    double target = clampRetention(targetRetention);
    double interval = s * (Math.pow(target, -1.0 / DECAY) - 1.0) / FACTOR;
    return Math.max(0.0, interval);
  }

  /**
   * 演化复习状态。
   *
   * @param stability 当前稳定度（天）；&le;0 视为无历史。
   * @param difficulty 当前难度 [0,1]。
   * @param elapsedDays 距上次复习的天数（无历史可传 0）。
   * @param outcome 本次复习结果。
   * @return 新的稳定度与难度。
   */
  public Projection next(
      double stability, double difficulty, double elapsedDays, ReviewOutcome outcome) {
    double d = clampUnit(difficulty <= 0 ? DIFFICULTY_BASELINE : difficulty);
    boolean fresh = stability <= 0;

    return switch (outcome) {
      case SUCCESS -> {
        double newStability;
        if (fresh) {
          newStability = SEED_SUCCESS;
        } else {
          double r = retrievability(stability, elapsedDays);
          double inc = 1.0 + W_SUCCESS * (1.0 - r) * (1.0 - 0.3 * d);
          newStability = Math.max(stability, stability * inc);
        }
        double newDifficulty = d + (DIFFICULTY_BASELINE - d) * DIFFICULTY_REVERSION;
        yield new Projection(clampStability(newStability), clampUnit(newDifficulty));
      }
      case RESTUDY -> {
        double newStability = fresh ? SEED_RESTUDY : Math.max(stability, stability * RESTUDY_MULT);
        yield new Projection(clampStability(newStability), d);
      }
      case LAPSE -> {
        double newStability = fresh ? SEED_LAPSE : stability * LAPSE_MULT;
        double newDifficulty = clampUnit(d + LAPSE_DIFFICULTY_UP);
        yield new Projection(clampStability(newStability), newDifficulty);
      }
    };
  }

  private static double clampStability(double value) {
    return Math.max(MIN_STABILITY, Math.min(MAX_STABILITY, value));
  }

  private static double clampUnit(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private static double clampRetention(double value) {
    return Math.max(0.50, Math.min(0.99, value));
  }

  /** 稳定度与难度的演化结果。 */
  public record Projection(double stability, double difficulty) {}
}
