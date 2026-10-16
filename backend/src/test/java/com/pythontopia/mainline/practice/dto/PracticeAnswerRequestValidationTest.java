package com.pythontopia.mainline.practice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeAnswerRequestValidationTest {

  private static final int MAX_ANSWERS = 32;

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsAnswerPayloadWithinLimits() {
    assertThat(
            validator.validate(
                new PracticeAnswerRequest(
                    Collections.nCopies(MAX_ANSWERS, "a".repeat(8192)))))
        .isEmpty();
  }

  @Test
  void rejectsTooManyAnswersOrAnOversizedAnswer() {
    assertThat(
            validator.validate(
                new PracticeAnswerRequest(
                    Collections.nCopies(MAX_ANSWERS + 1, "answer"))))
        .isNotEmpty();
    assertThat(validator.validate(new PracticeAnswerRequest(List.of("a".repeat(8193))))).isNotEmpty();
  }
}
