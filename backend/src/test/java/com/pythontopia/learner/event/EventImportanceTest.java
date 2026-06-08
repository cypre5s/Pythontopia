package com.pythontopia.learner.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 记忆流重要度规则的纯函数单测：写入即定、零 LLM、未知类型兜底=1。 */
class EventImportanceTest {

  @Test
  void scoresKnownEventTypesPerRule() {
    assertThat(EventImportance.of("practice_failed")).isEqualTo(4);
    assertThat(EventImportance.of("practice_abandoned")).isEqualTo(4);
    assertThat(EventImportance.of("comeback_solved")).isEqualTo(5);
    assertThat(EventImportance.of("debug_resolved")).isEqualTo(5);
    assertThat(EventImportance.of("practice_succeeded")).isEqualTo(3);
    assertThat(EventImportance.of("tutor_style_engaged")).isEqualTo(2);
    assertThat(EventImportance.of("probe_choice_made")).isEqualTo(2);
    assertThat(EventImportance.of("practice_retried")).isEqualTo(2);
    assertThat(EventImportance.of("profile_bootstrapped")).isEqualTo(2);
    assertThat(EventImportance.of("resource_consumed")).isEqualTo(1);
    assertThat(EventImportance.of("media_consumed")).isEqualTo(1);
  }

  @Test
  void unknownTypeFallsBackToOne() {
    assertThat(EventImportance.of("unknown_type")).isEqualTo(1);
    assertThat(EventImportance.of(null)).isEqualTo(1);
    assertThat(EventImportance.of("")).isEqualTo(1);
  }
}
