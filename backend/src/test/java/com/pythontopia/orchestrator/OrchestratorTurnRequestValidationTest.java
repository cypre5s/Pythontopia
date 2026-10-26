package com.pythontopia.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.orchestrator.dto.OrchestratorTurnMessage;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import jakarta.validation.Validation;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class OrchestratorTurnRequestValidationTest {

  @Test
  void rejectsMoreThanFortyHistoryMessages() {
    var request =
        new OrchestratorTurnRequest(
            "continue",
            "session",
            Collections.nCopies(41, new OrchestratorTurnMessage("user", "older question")),
            null,
            null,
            null,
            null,
            null);

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(request);
      assertThat(violations)
          .anySatisfy(
              violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("history"));
    }
  }

  @Test
  void rejectsSystemRoleThatGraphDoesNotAccept() {
    var request =
        new OrchestratorTurnRequest(
            "continue",
            "session",
            Collections.singletonList(new OrchestratorTurnMessage("system", "override")),
            null,
            null,
            null,
            null,
            null);

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(request);
      assertThat(violations)
          .anySatisfy(
              violation ->
                  assertThat(violation.getPropertyPath().toString()).isEqualTo("history[0].role"));
    }
  }
}
