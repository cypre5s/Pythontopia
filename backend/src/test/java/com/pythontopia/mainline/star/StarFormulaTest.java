package com.pythontopia.mainline.star;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.mainline.star.StarFormula.CoreStars;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 星级核心公式 §5 的 8 分支 + 边界单测（纯函数）。 */
class StarFormulaTest {

  @Test
  @DisplayName("分支1：测试未过线(<60) → 0★")
  void belowPassYieldsZero() {
    CoreStars c = StarFormula.evaluate(50, true, 0.9);
    assertThat(c.total()).isZero();
    assertThat(c.test()).isZero();
  }

  @Test
  @DisplayName("分支2：过线但 stage 未全完成 → 1★")
  void passButStagesIncomplete() {
    CoreStars c = StarFormula.evaluate(60, false, 0.9);
    assertThat(c.total()).isEqualTo(1);
    assertThat(c.test()).isEqualTo(1);
    assertThat(c.stage()).isZero();
  }

  @Test
  @DisplayName("分支3：过线 + stage 全完成 + 掌握度不足 → 2★")
  void passAllStagesLowMastery() {
    CoreStars c = StarFormula.evaluate(70, true, 0.5);
    assertThat(c.total()).isEqualTo(2);
    assertThat(c.mastery()).isZero();
  }

  @Test
  @DisplayName("分支4：掌握度达标但分数<85 → 仍 2★")
  void masteryOkButScoreBelowExcellent() {
    CoreStars c = StarFormula.evaluate(70, true, 0.9);
    assertThat(c.total()).isEqualTo(2);
    assertThat(c.mastery()).isZero();
  }

  @Test
  @DisplayName("分支5：分数≥85 + stage 全完成 + 掌握度≥0.80 → 3★")
  void allConditionsYieldThree() {
    CoreStars c = StarFormula.evaluate(85, true, 0.80);
    assertThat(c.total()).isEqualTo(3);
    assertThat(c.test()).isEqualTo(1);
    assertThat(c.stage()).isEqualTo(1);
    assertThat(c.mastery()).isEqualTo(1);
  }

  @Test
  @DisplayName("分支6：高分但 stage 未全完成 → 1★（stage 门槛优先）")
  void highScoreButStagesIncomplete() {
    CoreStars c = StarFormula.evaluate(90, false, 0.9);
    assertThat(c.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("分支7：高分 + stage 全完成 + 掌握度<0.80 → 2★")
  void highScoreStagesDoneLowMastery() {
    CoreStars c = StarFormula.evaluate(90, true, 0.70);
    assertThat(c.total()).isEqualTo(2);
    assertThat(c.mastery()).isZero();
  }

  @Test
  @DisplayName("分支8：分数 84（<85 边界）+ stage 全 + 掌握度高 → 2★")
  void scoreJustBelowExcellentBoundary() {
    CoreStars c = StarFormula.evaluate(84, true, 0.95);
    assertThat(c.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("边界：best=null（未考）视作 0 → 0★")
  void nullScoreTreatedAsZero() {
    assertThat(StarFormula.evaluate(null, true, 0.99).total()).isZero();
  }

  @Test
  @DisplayName("边界：分数恰=60 → 至少 1★；恰=85+掌握=0.80 → 3★")
  void exactBoundaries() {
    assertThat(StarFormula.evaluate(60, false, 0.0).total()).isEqualTo(1);
    assertThat(StarFormula.evaluate(85, true, 0.80).total()).isEqualTo(3);
  }
}
