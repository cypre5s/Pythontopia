package com.pythontopia.learner.mastery.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ReviewSchedulerTest {

  private final ReviewScheduler scheduler = new ReviewScheduler();

  @Test
  void retrievabilityAnchorsToNinetyPercentAtStability() {
    for (double stability : new double[] {1.0, 3.5, 9.0, 14.0, 30.0}) {
      assertThat(scheduler.retrievability(stability, stability)).isCloseTo(0.9, within(1e-9));
    }
  }

  @Test
  void retrievabilityStartsFullAndDecreasesOverTime() {
    assertThat(scheduler.retrievability(10.0, 0.0)).isCloseTo(1.0, within(1e-9));
    double early = scheduler.retrievability(10.0, 5.0);
    double late = scheduler.retrievability(10.0, 40.0);
    assertThat(early).isGreaterThan(late);
    assertThat(late).isBetween(0.0, 1.0);
  }

  @Test
  void intervalMatchesStabilityAtDefaultRetentionAndScalesWithTarget() {
    assertThat(scheduler.intervalDays(12.0, 0.9)).isCloseTo(12.0, within(1e-6));
    assertThat(scheduler.intervalDays(12.0, 0.95)).isLessThan(12.0);
    assertThat(scheduler.intervalDays(12.0, 0.80)).isGreaterThan(12.0);
  }

  @Test
  void successGrowsStabilityAndOverdueGrowsMore() {
    double onTime = scheduler.next(10.0, 0.3, 10.0, ReviewOutcome.SUCCESS).stability();
    double overdue = scheduler.next(10.0, 0.3, 40.0, ReviewOutcome.SUCCESS).stability();
    assertThat(onTime).isGreaterThanOrEqualTo(10.0);
    assertThat(overdue).isGreaterThan(onTime);
  }

  @Test
  void lapseShrinksStabilityAndRaisesDifficulty() {
    ReviewScheduler.Projection p = scheduler.next(20.0, 0.30, 30.0, ReviewOutcome.LAPSE);
    assertThat(p.stability()).isLessThan(20.0);
    assertThat(p.stability()).isGreaterThanOrEqualTo(ReviewScheduler.MIN_STABILITY);
    assertThat(p.difficulty()).isGreaterThan(0.30);
  }

  @Test
  void freshStateSeedsByOutcome() {
    assertThat(scheduler.next(0.0, 0.0, 0.0, ReviewOutcome.SUCCESS).stability()).isGreaterThan(1.0);
    assertThat(scheduler.next(0.0, 0.0, 0.0, ReviewOutcome.LAPSE).stability())
        .isBetween(ReviewScheduler.MIN_STABILITY, 1.5);
  }

  @Test
  void successDifficultyRevertsTowardBaseline() {
    double hard = scheduler.next(10.0, 0.90, 10.0, ReviewOutcome.SUCCESS).difficulty();
    assertThat(hard).isLessThan(0.90).isGreaterThan(ReviewScheduler.DIFFICULTY_BASELINE);
  }

  @Test
  void stabilityNeverExceedsCeiling() {
    double s = 300.0;
    for (int i = 0; i < 20; i++) {
      s = scheduler.next(s, 0.3, s * 2, ReviewOutcome.SUCCESS).stability();
    }
    assertThat(s).isLessThanOrEqualTo(ReviewScheduler.MAX_STABILITY);
  }
}
