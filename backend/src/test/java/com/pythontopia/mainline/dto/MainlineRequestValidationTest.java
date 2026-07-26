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
 *   <li>无尽小结：单字段 {@code @Min/@Max} 挡不住 {@code correct > total} 这类跨字段构造
 *       （{@code {"chapter":1,"correct":8,"total":1}} → 正确率 8.0 直接达标白拿 1★）。
 *   <li>批量页阅读：无条数上限时可把整章 116 个 pageId 一次灌回。
 * </ul>
 */
class MainlineRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void endlessSummaryAcceptsPlausibleRound() {
    assertThat(validator.validate(new EndlessSummaryRequest(1, 8, 10, 5))).isEmpty();
  }

  @Test
  void endlessSummaryRejectsCorrectAboveTotal() {
    assertThat(validator.validate(new EndlessSummaryRequest(1, 8, 1, 0))).isNotEmpty();
  }

  @Test
  void endlessSummaryRejectsStreakAboveTotal() {
    assertThat(validator.validate(new EndlessSummaryRequest(1, 10, 10, 11))).isNotEmpty();
  }

  @Test
  void endlessSummaryRejectsAbsurdRoundSize() {
    assertThat(validator.validate(new EndlessSummaryRequest(1, 100_000, 100_000, 0))).isNotEmpty();
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
}
