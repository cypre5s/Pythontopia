package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.learner.profile.dto.SelfDimensionRequest;
import java.lang.reflect.RecordComponent;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Self-report may update subjective values, but cannot create trusted evidence or arbitrary metadata.
 */
class SelfDimensionRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void selfReportCannotSupplyEvidenceCountOrArbitraryMetadata() {
    assertThat(Arrays.stream(SelfDimensionRequest.class.getRecordComponents())
            .map(RecordComponent::getName))
        .containsExactly("scoreDelta", "tagsAdd");
  }

  @Test
  void validatesOnlySubjectiveSelfReportFields() {
    assertThat(validator.validate(new SelfDimensionRequest(0.1, List.of("visual"))))
        .isEmpty();
    assertThat(validator.validate(new SelfDimensionRequest(-0.2, null))).isEmpty();
    assertThat(validator.validate(new SelfDimensionRequest(null, List.of("video"))))
        .isEmpty();
  }
}
