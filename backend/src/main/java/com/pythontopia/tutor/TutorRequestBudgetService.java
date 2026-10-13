package com.pythontopia.tutor;

import com.pythontopia.common.error.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Atomically reserves one account-wide daily slot before dispatching a Tutor model request. */
@Service
public class TutorRequestBudgetService {

  private static final int MAX_DAILY_REQUESTS = 30;
  private static final ZoneId RESET_ZONE = ZoneId.of("Asia/Shanghai");

  private final JdbcTemplate jdbcTemplate;

  public TutorRequestBudgetService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Reserves a top-level Tutor request. Reservations count failed model dispatches too, preventing
   * repeated retries from bypassing the account limit. A single PostgreSQL upsert makes the limit
   * effective across backend instances.
   */
  public void reserve(Long userId) {
    Objects.requireNonNull(userId, "userId");
    LocalDate usageDate = LocalDate.now(RESET_ZONE);
    int updated =
        jdbcTemplate.update(
            "INSERT INTO tutor_model_daily_usage (user_id, usage_date, request_count)"
                + " VALUES (?, ?, 1)"
                + " ON CONFLICT (user_id) DO UPDATE SET"
                + " usage_date = EXCLUDED.usage_date,"
                + " request_count = CASE"
                + " WHEN tutor_model_daily_usage.usage_date = EXCLUDED.usage_date"
                + " THEN tutor_model_daily_usage.request_count + 1 ELSE 1 END,"
                + " updated_at = now()"
                + " WHERE tutor_model_daily_usage.usage_date <> EXCLUDED.usage_date"
                + " OR tutor_model_daily_usage.request_count < ?",
            userId,
            usageDate,
            MAX_DAILY_REQUESTS);
    if (updated != 1) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "TUTOR_DAILY_REQUEST_LIMIT_REACHED",
          "今日导师请求次数已达上限，请明天再试");
    }
  }
}
