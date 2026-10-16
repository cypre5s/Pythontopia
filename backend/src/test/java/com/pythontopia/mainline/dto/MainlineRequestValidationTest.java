package com.pythontopia.mainline.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 主线打点请求体的边界校验。
 *
 * <ul>
 *   <li>无尽补星只接受章节和 UUID runId，不再接收客户端自报的成绩。
 *   <li>批量页阅读：无条数上限时可把整章 116 个 pageId 一次灌回。
 * </ul>
 */
class MainlineRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void endlessSummaryAcceptsRunId() {
    assertThat(
            validator.validate(
                new EndlessSummaryRequest(1, "123e4567-e89b-12d3-a456-426614174000")))
        .isEmpty();
  }

  @Test
  void endlessSummaryRejectsMalformedRunId() {
    assertThat(validator.validate(new EndlessSummaryRequest(1, "client-claimed-score")))
        .isNotEmpty();
  }

  @Test
  void pagesReadAcceptsBatchWithinCap() {
    assertThat(
            validator.validate(
                new PagesReadRequest(Collections.nCopies(PagesReadRequest.MAX_BATCH_PAGES, 1L))))
        .isEmpty();
    assertThat(validator.validate(new PagesReadRequest(List.of()))).isEmpty();
  }

  @Test
  void pagesReadRejectsOversizedBatch() {
    assertThat(
            validator.validate(
                new PagesReadRequest(Collections.nCopies(PagesReadRequest.MAX_BATCH_PAGES + 1, 1L))))
        .isNotEmpty();
  }

  @Test
  void videoProgressAllowsZeroToStartServerClockButRejectsNegativeValues() {
    assertThat(validator.validate(new VideoProgressRequest(0))).isEmpty();
    assertThat(validator.validate(new VideoProgressRequest(1))).isEmpty();
    assertThat(validator.validate(new VideoProgressRequest(-1))).isNotEmpty();
    assertThat(validator.validate(new VideoProgressRequest(null))).isNotEmpty();
  }
}
