package com.pythontopia.world.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class PlayerStatePatchValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsTilesInsideTownHub() {
    assertThat(validator.validate(new PlayerStatePatch(0, 0, "DOWN"))).isEmpty();
    assertThat(validator.validate(new PlayerStatePatch(49, 39, "DOWN"))).isEmpty();
  }

  @Test
  void rejectsTilesOutsideTownHub() {
    assertThat(validator.validate(new PlayerStatePatch(-1, 0, "DOWN"))).isNotEmpty();
    assertThat(validator.validate(new PlayerStatePatch(50, 0, "DOWN"))).isNotEmpty();
    assertThat(validator.validate(new PlayerStatePatch(0, -1, "DOWN"))).isNotEmpty();
    assertThat(validator.validate(new PlayerStatePatch(0, 40, "DOWN"))).isNotEmpty();
  }
}
