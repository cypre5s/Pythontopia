package com.pythontopia.mainline.star;

/**
 * 章末星级核心公式（§5，纯函数，便于 8 分支单测）。
 * 1★=测试最高分≥60；2★=1★且本章所有 stage 完成；3★=2★且(KC 均掌握≥0.80 且 测试≥85)。
 * 补星(ERRORBOOK/ENDLESS)不在此，由 ChapterStarService.applyRecoveryStar 叠加。
 */
public final class StarFormula {

  public static final int PASS_SCORE = 60; // 1★ / 测试通过线
  public static final int EXCELLENT_SCORE = 85; // 3★ 的测试门槛
  public static final double MASTERY_THRESHOLD = 0.80; // 3★ 的本章 KC 平均掌握度门槛
  public static final int MAX_STARS = 3;

  private StarFormula() {}

  /** bestTestScore：历史最高分(null=0)；allStagesComplete：三件套全完成；avgMastery：本章 KC 均掌握度(0..1)。 */
  public static CoreStars evaluate(
      Integer bestTestScore, boolean allStagesComplete, double avgMastery) {
    int best = bestTestScore == null ? 0 : bestTestScore;
    int testStar = best >= PASS_SCORE ? 1 : 0;
    int stageStar = (testStar == 1 && allStagesComplete) ? 1 : 0;
    int masteryStar =
        (stageStar == 1 && avgMastery >= MASTERY_THRESHOLD && best >= EXCELLENT_SCORE) ? 1 : 0;
    return new CoreStars(testStar, stageStar, masteryStar);
  }

  /** 核心星级构成：total()=test+stage+mastery(0..3)，对应 breakdown 三键。 */
  public record CoreStars(int test, int stage, int mastery) {

    public int total() {
      return test + stage + mastery;
    }
  }
}
