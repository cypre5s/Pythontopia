package com.pythontopia.mainline.star;

/**
 * 章末星级<b>核心公式</b>（r15 计划 §5，已确认）——纯函数，便于单测 8 分支。
 *
 * <ul>
 *   <li><b>1★</b>：章末大测试最高分 ≥ 60。
 *   <li><b>2★</b>：1★ 且本章所有 stage 完成（视频+课件+例题三件套全过线）。
 *   <li><b>3★</b>：2★ 且（本章 KC 平均掌握度 ≥ 0.80 <b>且</b> 大测试最高分 ≥ 85）。
 * </ul>
 *
 * <p>补星（ERRORBOOK_RECOVERY / ENDLESS_RECOVERY，每途径至多 +1、封顶 3★）<b>不</b>在本核心公式内，
 * 由 Wave 2 子代理 B1 在 {@link ChapterStarService} 的补星钩子叠加（见该类 TODO）。
 */
public final class StarFormula {

  public static final int PASS_SCORE = 60; // 1★ / 测试通过线
  public static final int EXCELLENT_SCORE = 85; // 3★ 的测试门槛
  public static final double MASTERY_THRESHOLD = 0.80; // 3★ 的本章 KC 平均掌握度门槛
  public static final int MAX_STARS = 3;

  private StarFormula() {}

  /**
   * @param bestTestScore 本章大测试历史最高分；{@code null} = 尚未通过任何一次（视作 0）。
   * @param allStagesComplete 本章是否所有 stage 三件套完成。
   * @param avgMastery 本章 KC 平均掌握度（0..1）。
   */
  public static CoreStars evaluate(
      Integer bestTestScore, boolean allStagesComplete, double avgMastery) {
    int best = bestTestScore == null ? 0 : bestTestScore;
    int testStar = best >= PASS_SCORE ? 1 : 0;
    int stageStar = (testStar == 1 && allStagesComplete) ? 1 : 0;
    int masteryStar =
        (stageStar == 1 && avgMastery >= MASTERY_THRESHOLD && best >= EXCELLENT_SCORE) ? 1 : 0;
    return new CoreStars(testStar, stageStar, masteryStar);
  }

  /**
   * 核心星级构成。{@link #total()} = test + stage + mastery（0..3），与 chapter_star.breakdown 的
   * {@code {test,stage,mastery}} 三键一一对应。
   */
  public record CoreStars(int test, int stage, int mastery) {

    public int total() {
      return test + stage + mastery;
    }
  }
}
