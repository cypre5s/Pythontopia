package com.pythontopia.npc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AffinityTierTest {

  @Test
  void tierIsFloorClampedToZeroToFour() {
    assertThat(NpcAffinityService.tierForScore(-1.0)).isZero();
    assertThat(NpcAffinityService.tierForScore(0.99)).isZero();
    assertThat(NpcAffinityService.tierForScore(1.0)).isEqualTo(1);
    assertThat(NpcAffinityService.tierForScore(3.75)).isEqualTo(3);
    assertThat(NpcAffinityService.tierForScore(9.0)).isEqualTo(4);
  }
}
