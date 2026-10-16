package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.feynman.dto.FeynmanDtos.TeachRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class FeynmanRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void rejectsOversizedTeachingExplanation() {
    var violations = validator.validate(new TeachRequest("x".repeat(2001)));

    assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("explanation");
  }
}
