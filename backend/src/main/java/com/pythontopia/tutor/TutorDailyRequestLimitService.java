package com.pythontopia.tutor;

import com.pythontopia.common.error.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic per-account and shared provider daily caps for user-triggered AI requests. */
@Service
public class TutorDailyRequestLimitService {

  static final int DAILY_LIMIT = 30;
  static final int PROVIDER_DAILY_LIMIT = 300;
  private static final ZoneId RESET_ZONE = ZoneId.of("Asia/Shanghai");
  private static final String RESERVE_SQL =
      """
      INSERT INTO tutor_daily_request_usage (user_id, usage_date, request_count, updated_at)
      VALUES (?, ?, 1, now())
      ON CONFLICT (user_id, usage_date) DO UPDATE
      SET request_count = tutor_daily_request_usage.request_count + 1,
          updated_at = now()
      WHERE tutor_daily_request_usage.request_count < ?
      RETURNING request_count
      """;
  private static final String RESERVE_PROVIDER_SQL =
      """
      INSERT INTO ai_provider_daily_request_usage (usage_date, request_count, updated_at)
      VALUES (?, 1, now())
      ON CONFLICT (usage_date) DO UPDATE
      SET request_count = ai_provider_daily_request_usage.request_count + 1,
          updated_at = now()
      WHERE ai_provider_daily_request_usage.request_count < ?
      RETURNING request_count
      """;

  private final JdbcTemplate jdbc;

  public TutorDailyRequestLimitService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Reserves one request before contacting the model; conversation IDs do not affect the cap. */
  @Transactional
  public void reserve(Long userId) {
    LocalDate usageDate = LocalDate.now(RESET_ZONE);
    List<Integer> counts =
        jdbc.query(
            RESERVE_SQL,
            (rs, rowNum) -> rs.getInt("request_count"),
            userId,
            usageDate,
            DAILY_LIMIT);
    if (counts.isEmpty()) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "TUTOR_DAILY_LIMIT_REACHED", "今日 AI 请求额度已达 30 次，请明天再试");
    }
    List<Integer> providerCounts =
        jdbc.query(
            RESERVE_PROVIDER_SQL,
            (rs, rowNum) -> rs.getInt("request_count"),
            usageDate,
            PROVIDER_DAILY_LIMIT);
    if (providerCounts.isEmpty()) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "AI_PROVIDER_DAILY_LIMIT_REACHED", "今日全站 AI 请求额度已用完，请明天再试");
    }
  }

  @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Shanghai")
  public void removeExpiredUsage() {
    jdbc.update(
        "DELETE FROM tutor_daily_request_usage WHERE usage_date < ?",
        LocalDate.now(RESET_ZONE).minusDays(90));
    jdbc.update(
        "DELETE FROM ai_provider_daily_request_usage WHERE usage_date < ?",
        LocalDate.now(RESET_ZONE).minusDays(90));
  }
}
