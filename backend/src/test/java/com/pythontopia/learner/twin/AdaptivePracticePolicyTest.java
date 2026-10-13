package com.pythontopia.learner.twin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdaptivePracticePolicyTest {

  private final AdaptivePracticePolicy policy = new AdaptivePracticePolicy();

  @Test
  void prioritizesDueReviewThenRecentFailureThenGoalAndUnassessedKnowledge() {
    var picks = policy.select(
        List.of(
            kc("KC-DUE", 1, true, 0.5, true, List.of(true), List.of()),
            kc("KC-FAIL", 1, true, 0.4, false, List.of(false, false), List.of()),
            kc("KC-GOAL", 2, true, 0.7, false, List.of(true), List.of()),
            kc("KC-NEW", 2, false, null, false, List.of(), List.of())),
        Set.of("KC-GOAL"), null, List.of(), 4);

    assertThat(picks).extracting(AdaptivePracticePolicy.Pick::kcCode)
        .containsExactly("KC-DUE", "KC-FAIL", "KC-GOAL", "KC-NEW");
    assertThat(picks.get(1).action()).isEqualTo("REMEDIATION");
    assertThat(picks.get(1).reason()).contains("连续两次");
  }

  @Test
  void preferredEligibleKnowledgePointHeadsTheExpeditionFrontier() {
    var picks = policy.select(
        List.of(
            kc("KC-DUE", 1, true, 0.5, true, List.of(true), List.of()),
            kc("KC-SELECTED", 1, false, null, false, List.of(), List.of())),
        Set.of(), null, List.of(), 2, "KC-SELECTED");

    assertThat(picks).extracting(AdaptivePracticePolicy.Pick::kcCode)
        .containsExactly("KC-SELECTED", "KC-DUE");

    var prerequisiteChecked = policy.select(
        List.of(
            kc("KC-FOUNDATION", 1, false, null, false, List.of(), List.of()),
            kc("KC-BLOCKED", 2, true, 0.3, false, List.of(false, false), List.of("KC-FOUNDATION")),
            kc("KC-OTHER", 2, false, null, false, List.of(), List.of())),
        Set.of(), null, List.of(), 1, "KC-BLOCKED");
    assertThat(prerequisiteChecked).extracting(AdaptivePracticePolicy.Pick::kcCode)
        .doesNotContain("KC-BLOCKED");
  }

  @Test
  void adjustsDifficultyByAtMostOneOnlyAfterTwoMatchingVerifiedResults() {
    var passed = policy.select(List.of(
        kc("KC-PASS", 1, true, 0.5, false, List.of(true, true), List.of())),
        Set.of(), null, List.of(), 1).getFirst();
    var failed = policy.select(List.of(
        kc("KC-FAIL", 1, true, 0.5, false, List.of(false, false), List.of())),
        Set.of(), null, List.of(), 1).getFirst();
    var mixed = policy.select(List.of(
        kc("KC-MIXED", 1, true, 0.5, false, List.of(false, true), List.of())),
        Set.of(), null, List.of(), 1).getFirst();

    assertThat(passed.difficulty()).isEqualTo(4);
    assertThat(failed.difficulty()).isEqualTo(2);
    assertThat(mixed.difficulty()).isEqualTo(3);
  }

  @Test
  void excludesUnavailablePrerequisiteTopicsAndKeepsThePrequisitePlayable() {
    var picks = policy.select(
        List.of(
            kc("KC-FOUNDATION", 1, false, null, false, List.of(), List.of()),
            kc("KC-ADVANCED", 2, true, 0.3, false, List.of(false, false), List.of("KC-FOUNDATION"))),
        Set.of(), null, List.of(), 2);

    assertThat(picks).extracting(AdaptivePracticePolicy.Pick::kcCode)
        .contains("KC-FOUNDATION")
        .doesNotContain("KC-ADVANCED");
  }

  @Test
  void usesAnotherTopicWhenExcludedAndAllowsRepeatOnlyWhenNoAlternativeExists() {
    var topics = List.of(
        kc("KC-A", 1, true, 0.4, false, List.of(), List.of()),
        kc("KC-B", 1, true, 0.3, false, List.of(), List.of()));

    assertThat(policy.select(topics, Set.of(), null, List.of("KC-A"), 1).getFirst().kcCode())
        .isEqualTo("KC-B");
    assertThat(policy.select(topics.subList(0, 1), Set.of(), null, List.of("KC-A"), 1).getFirst().kcCode())
        .isEqualTo("KC-A");
  }

  private static AdaptivePracticePolicy.Candidate kc(
      String code,
      int chapter,
      boolean assessed,
      Double mastery,
      boolean due,
      List<Boolean> recentOutcomes,
      List<String> prerequisites) {
    return new AdaptivePracticePolicy.Candidate(
        code, code, chapter, assessed, mastery, assessed ? 0.6 : null,
        due, null, null, recentOutcomes, prerequisites);
  }
}
