package com.pythontopia.bugmine.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BugMineProfileAdvisorTest {

  private final BugMineProfileAdvisor advisor = new BugMineProfileAdvisor();

  @Test
  void strongMasteryTargetsShallowDepth() {
    assertThat(advisor.recommendedDepthTarget(new BigDecimal("0.820"), false)).isEqualTo(2);
  }

  @Test
  void weakMasteryWithPatternTargetsDeepVariant() {
    assertThat(advisor.recommendedDepthTarget(new BigDecimal("0.120"), true)).isEqualTo(5);
  }

  @Test
  void guidanceMentionsVariantForDeepTargets() {
    String guidance = advisor.guidanceLabel(5, true);
    assertThat(guidance).contains("L5");
  }
}
