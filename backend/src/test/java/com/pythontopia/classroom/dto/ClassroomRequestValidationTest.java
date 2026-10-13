package com.pythontopia.classroom.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.classroom.controller.TeacherClassroomController.UpdateClassroomRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ClassroomRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void createAndUpdateUseTheSameClassroomFieldLimits() {
    String name = "n".repeat(128);
    String description = "d".repeat(1000);

    assertThat(validator.validate(new CreateClassroomRequest(name, description))).isEmpty();
    assertThat(validator.validate(new UpdateClassroomRequest(name, description))).isEmpty();
    assertThat(validator.validate(new CreateClassroomRequest(name, "d".repeat(1001)))).isNotEmpty();
    assertThat(validator.validate(new UpdateClassroomRequest(name, "d".repeat(1001)))).isNotEmpty();
  }
}
