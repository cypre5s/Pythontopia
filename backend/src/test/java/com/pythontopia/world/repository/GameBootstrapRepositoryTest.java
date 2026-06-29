package com.pythontopia.world.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameBootstrapRepositoryTest {

  @Test
  void normalizeMapKeyConvertsRetiredR14SceneKeysToTownHub() {
    assertThat(GameBootstrapRepository.normalizeMapKey("town-beta")).isEqualTo("town_hub");
    assertThat(GameBootstrapRepository.normalizeMapKey("farm_north")).isEqualTo("town_hub");
    assertThat(GameBootstrapRepository.normalizeMapKey("mine-south")).isEqualTo("town_hub");
    assertThat(GameBootstrapRepository.normalizeMapKey(null)).isEqualTo("town_hub");
    assertThat(GameBootstrapRepository.normalizeMapKey("town_hub")).isEqualTo("town_hub");
  }
}
