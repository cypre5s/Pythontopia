package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TutorRequestBudgetServiceTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

  // 回归：训练场导学同时受 TutorRequestBudgetService（固定 30 次/天）和
  // TutorDailyRequestLimitService（开关可关）两道限额约束。只关开关不关这里，
  // 演示/联调期仍然会被 429 拦住。
  @Test
  void disabledLimitsSkipReservationEntirely() {
    var service = new TutorRequestBudgetService(jdbc, false);

    service.reserve(7L);

    verifyNoInteractions(jdbc);
  }

  @Test
  void reservedSlotCountsOneRequest() {
    when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);
    var service = new TutorRequestBudgetService(jdbc, true);

    service.reserve(7L);

    verify(jdbc).update(anyString(), any(), any(), any());
  }

  @Test
  void exhaustedDailyQuotaThrowsTooManyRequests() {
    when(jdbc.update(anyString(), any(), any(), any())).thenReturn(0);
    var service = new TutorRequestBudgetService(jdbc, true);

    assertThatThrownBy(() -> service.reserve(7L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.status().value()).isEqualTo(429);
              assertThat(exception.code()).isEqualTo("TUTOR_DAILY_REQUEST_LIMIT_REACHED");
            });
  }
}
