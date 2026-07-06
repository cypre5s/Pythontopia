package com.pythontopia.npc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NpcMemoryDedupTest {

  @Test
  void nearDuplicateWhenCosineStrictlyAboveThreshold() {
    assertThat(NpcMemoryDedup.isNearDuplicate(vec(1.0, 0.0), vec(0.93, 0.36756), 0.92))
        .isTrue();
  }

  @Test
  void notDuplicateWhenCosineBelowThreshold() {
    assertThat(NpcMemoryDedup.isNearDuplicate(vec(1.0, 0.0), vec(0.91, 0.41461), 0.92))
        .isFalse();
  }

  @Test
  void invalidVectorsAreNotDuplicates() {
    assertThat(NpcMemoryDedup.isNearDuplicate(null, vec(1.0, 0.0), 0.92)).isFalse();
    assertThat(NpcMemoryDedup.isNearDuplicate(vec(1.0), vec(1.0, 0.0), 0.92)).isFalse();
    assertThat(NpcMemoryDedup.isNearDuplicate(vec(0.0, 0.0), vec(1.0, 0.0), 0.92)).isFalse();
  }

  private static float[] vec(double... values) {
    float[] result = new float[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (float) values[i];
    }
    return result;
  }
}
