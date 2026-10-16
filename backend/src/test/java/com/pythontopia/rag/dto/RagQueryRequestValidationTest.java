package com.pythontopia.rag.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagQueryRequestValidationTest {

  @Test
  void rejectsFilterListsLargerThanTheCourse() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      RagQueryRequestDto request =
          new RagQueryRequestDto(
              "python",
              8,
              java.util.Collections.nCopies(66, "PY43-KC-001"),
              List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L),
              0.3);

      assertThat(validator.validate(request))
          .extracting(violation -> violation.getPropertyPath().toString())
          .contains("kcCodes", "chapterIds");
    }
  }

  @Test
  void rejectsOversizedKnowledgePointCodes() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      RagQueryRequestDto request =
          new RagQueryRequestDto("python", 8, List.of("x".repeat(33)), List.of(1L), 0.3);

      assertThat(validator.validate(request)).isNotEmpty();
    }
  }
}
