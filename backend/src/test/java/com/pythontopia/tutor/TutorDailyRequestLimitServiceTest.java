package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.common.error.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TutorDailyRequestLimitServiceTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final TutorDailyRequestLimitService service = new TutorDailyRequestLimitService(jdbc, true);

  @Test
  void reservesOneOfThirtyRequestsAtomicallyForTheAccountAndShanghaiDate() {
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of(1));
    when(jdbc.query(
            anyString(),
            any(RowMapper.class),
            any(LocalDate.class),
            org.mockito.ArgumentMatchers.eq(300)))
        .thenReturn(List.of(1));

    service.reserve(42L);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .query(
            sql.capture(),
            any(RowMapper.class),
            org.mockito.ArgumentMatchers.eq(42L),
            any(LocalDate.class),
            org.mockito.ArgumentMatchers.eq(30));
    assertThat(sql.getValue())
        .contains("ON CONFLICT", "request_count < ?", "RETURNING request_count");
    ArgumentCaptor<String> providerSql = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .query(
            providerSql.capture(),
            any(RowMapper.class),
            any(LocalDate.class),
            org.mockito.ArgumentMatchers.eq(300));
    assertThat(providerSql.getValue()).contains("ai_provider_daily_request_usage", "ON CONFLICT");
  }

  @Test
  void localUnlimitedModeDoesNotReserveAccountOrProviderUsage() {
    TutorDailyRequestLimitService unlimitedService =
        new TutorDailyRequestLimitService(jdbc, false);

    unlimitedService.reserve(42L);

    verifyNoInteractions(jdbc);
  }

  @Test
  void reservesBothCapsAgainstTheSameDateWhenRequestCrossesMidnight() {
    LocalDate beforeMidnight = LocalDate.of(2026, 10, 14);
    LocalDate afterMidnight = beforeMidnight.plusDays(1);
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of(1));
    when(jdbc.query(
            anyString(),
            any(RowMapper.class),
            any(LocalDate.class),
            org.mockito.ArgumentMatchers.eq(300)))
        .thenReturn(List.of(1));

    try (MockedStatic<LocalDate> dates = org.mockito.Mockito.mockStatic(LocalDate.class)) {
      dates
          .when(() -> LocalDate.now(ZoneId.of("Asia/Shanghai")))
          .thenReturn(beforeMidnight, afterMidnight);

      service.reserve(42L);
    }

    ArgumentCaptor<LocalDate> accountDate = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> providerDate = ArgumentCaptor.forClass(LocalDate.class);
    verify(jdbc)
        .query(
            anyString(),
            any(RowMapper.class),
            org.mockito.ArgumentMatchers.eq(42L),
            accountDate.capture(),
            org.mockito.ArgumentMatchers.eq(30));
    verify(jdbc)
        .query(
            anyString(),
            any(RowMapper.class),
            providerDate.capture(),
            org.mockito.ArgumentMatchers.eq(300));

    assertThat(providerDate.getValue()).isEqualTo(accountDate.getValue());
  }

  @Test
  void rejectsTheThirtyFirstRequestWithTooManyRequests() {
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.reserve(42L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(error.code()).isEqualTo("TUTOR_DAILY_LIMIT_REACHED");
            });
  }

  @Test
  void rejectsRequestsAcrossAccountsAfterTheSharedProviderCap() {
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of(1));
    when(jdbc.query(
            anyString(),
            any(RowMapper.class),
            any(LocalDate.class),
            org.mockito.ArgumentMatchers.eq(300)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.reserve(99L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(error.code()).isEqualTo("AI_PROVIDER_DAILY_LIMIT_REACHED");
            });
  }

  @Test
  void providerOnlyReservationSkipsThePerAccountCap() {
    when(jdbc.query(
            anyString(),
            any(RowMapper.class),
            any(LocalDate.class),
            org.mockito.ArgumentMatchers.eq(300)))
        .thenReturn(List.of(1));

    service.reserveProviderOnly();

    // 只对全站 provider 行计数，不碰 tutor_daily_request_usage（不扣学生个人额度）。
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .query(
            sql.capture(),
            any(RowMapper.class),
            any(LocalDate.class),
            org.mockito.ArgumentMatchers.eq(300));
    assertThat(sql.getValue())
        .contains("ai_provider_daily_request_usage")
        .doesNotContain("tutor_daily_request_usage");
  }

  @Test
  void providerOnlyReservationStillFailsWhenProviderBudgetIsExhausted() {
    when(jdbc.query(
            anyString(),
            any(RowMapper.class),
            any(LocalDate.class),
            org.mockito.ArgumentMatchers.eq(300)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.reserveProviderOnly())
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("AI_PROVIDER_DAILY_LIMIT_REACHED"));
  }

  @Test
  void retainsOnlyNinetyDaysOfDailyUsageRows() {
    service.removeExpiredUsage();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<LocalDate> cutoff = ArgumentCaptor.forClass(LocalDate.class);
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2))
        .update(sql.capture(), cutoff.capture());
    assertThat(sql.getAllValues())
        .anySatisfy(statement -> assertThat(statement).contains("tutor_daily_request_usage"))
        .anySatisfy(statement -> assertThat(statement).contains("ai_provider_daily_request_usage"));
    assertThat(cutoff.getAllValues())
        .allMatch(
            date -> date.equals(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(90)));
  }
}
