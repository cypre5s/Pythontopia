package com.pythontopia.npc;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.npc.dto.NpcDialogRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class NpcDialogRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void rejectsMessagesLongerThanTwoThousandCharacters() {
    assertThat(validator.validate(new NpcDialogRequest("x".repeat(2001)))).isNotEmpty();
  }

  @Test
  void acceptsMessagesAtTheLimit() {
    assertThat(validator.validate(new NpcDialogRequest("x".repeat(2000)))).isEmpty();
  }
}
