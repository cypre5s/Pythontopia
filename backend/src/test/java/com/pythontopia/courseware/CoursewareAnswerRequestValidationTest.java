package com.pythontopia.courseware;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.courseware.dto.CoursewareAnswerRequestDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoursewareAnswerRequestValidationTest {

  @Test
  void acceptsCourseLimitsAndTopKRange() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      CoursewareAnswerRequestDto request =
          new CoursewareAnswerRequestDto("question", Collections.nCopies(65, "x".repeat(32)), 12);

      assertThat(validator.validate(request)).isEmpty();
    }
  }

  @Test
  void rejectsOversizedKnowledgePointFiltersAndTopK() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(
              validator.validate(
                  new CoursewareAnswerRequestDto(
                      "question", Collections.nCopies(66, "PY43-KC-001"), 6)))
          .extracting(violation -> violation.getPropertyPath().toString())
          .contains("kcCodes");
      assertThat(
              validator.validate(
                  new CoursewareAnswerRequestDto("question", List.of("x".repeat(33)), 6)))
          .extracting(violation -> violation.getPropertyPath().toString())
          .anyMatch(path -> path.startsWith("kcCodes"));
      assertThat(validator.validate(new CoursewareAnswerRequestDto("question", List.of(), 0)))
          .extracting(violation -> violation.getPropertyPath().toString())
          .contains("topK");
      assertThat(validator.validate(new CoursewareAnswerRequestDto("question", List.of(), 13)))
          .extracting(violation -> violation.getPropertyPath().toString())
          .contains("topK");
    }
  }
}
