package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.feynman.dto.FeynmanDtos.StartRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class FeynmanStartRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void rejectsKnowledgePointLongerThan64Characters() {
    assertThat(validator.validate(new StartRequest("x".repeat(65)))).isNotEmpty();
  }

  @Test
  void acceptsKnowledgePointAtMaximumLength() {
    assertThat(validator.validate(new StartRequest("x".repeat(64)))).isEmpty();
  }
}
