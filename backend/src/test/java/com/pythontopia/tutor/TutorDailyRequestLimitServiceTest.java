package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.common.error.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.http.HttpStatus;

class TutorDailyRequestLimitServiceTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final TutorDailyRequestLimitService service = new TutorDailyRequestLimitService(jdbc);

  @Test
  void reservesOneOfThirtyRequestsAtomicallyForTheAccountAndShanghaiDate() {
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of(1));

    service.reserve(42L);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), org.mockito.ArgumentMatchers.eq(42L),
        any(LocalDate.class), org.mockito.ArgumentMatchers.eq(30));
    assertThat(sql.getValue()).contains("ON CONFLICT", "request_count < ?", "RETURNING request_count");
  }

  @Test
  void rejectsTheThirtyFirstRequestWithTooManyRequests() {
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.reserve(42L))
        .isInstanceOfSatisfying(BusinessException.class, error -> {
          assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
          assertThat(error.code()).isEqualTo("TUTOR_DAILY_LIMIT_REACHED");
        });
  }

  @Test
  void retainsOnlyNinetyDaysOfDailyUsageRows() {
    service.removeExpiredUsage();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<LocalDate> cutoff = ArgumentCaptor.forClass(LocalDate.class);
    verify(jdbc).update(sql.capture(), cutoff.capture());
    assertThat(sql.getValue()).contains("usage_date < ?");
    assertThat(cutoff.getValue()).isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(90));
  }
}
