package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.learner.profile.dto.SelfDimensionRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 自评 PUT 的 evidenceIncrement 必须有界。
 *
 * <p>放开上界：一次 {@code {"evidenceIncrement": 999}} 就把 confidence 顶到 1.0，学生自编的 tag 随即越过
 * profile_brief 的 0.3 门槛注入 Tutor / NPC prompt。放开下界：{@code -999} 让 evidence_count 变负，
 * 该生画像对全部下游永久静默。
 */
class SelfDimensionRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void rejectsEvidenceIncrementAboveTheSelfReportCeiling() {
    assertThat(validator.validate(new SelfDimensionRequest(0.1, 999, null, null))).isNotEmpty();
    assertThat(validator.validate(new SelfDimensionRequest(null, 6, null, null))).isNotEmpty();
  }

  @Test
  void rejectsNegativeEvidenceIncrement() {
    assertThat(validator.validate(new SelfDimensionRequest(null, -999, null, null))).isNotEmpty();
    assertThat(validator.validate(new SelfDimensionRequest(null, -1, null, null))).isNotEmpty();
  }

  @Test
  void acceptsBoundedSelfReport() {
    assertThat(validator.validate(new SelfDimensionRequest(0.1, 0, List.of("visual"), null)))
        .isEmpty();
    assertThat(validator.validate(new SelfDimensionRequest(-0.2, 5, null, null))).isEmpty();
    assertThat(validator.validate(new SelfDimensionRequest(null, null, List.of("video"), null)))
        .isEmpty();
  }
}
