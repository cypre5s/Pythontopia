package com.pythontopia.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.intent.dto.IntentRouteRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntentRouteRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void boundsKcCodeListsAndElements() {
    var request =
        new IntentRouteRequest(
            "帮我练习",
            java.util.Collections.nCopies(66, "PY43-KC-001"),
            java.util.Collections.nCopies(
                66, new IntentRouteRequest.CourseKc("x".repeat(33), "变量")),
            null,
            List.of("x".repeat(33)),
            List.of(),
            null,
            List.of("x".repeat(33)),
            List.of(),
            null,
            null,
            null,
            null,
            null);

    var properties =
        validator.validate(request).stream()
            .map(violation -> violation.getPropertyPath().toString())
            .toList();

    assertThat(properties)
        .contains(
            "manualKcCodes",
            "courseKcs",
            "courseKcs[0].code",
            "lastKcCodes[0].<list element>",
            "recentKcCodes[0].<list element>");
  }
}
