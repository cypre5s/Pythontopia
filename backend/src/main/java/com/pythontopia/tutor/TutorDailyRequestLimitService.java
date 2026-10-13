package com.pythontopia.tutor;

import com.pythontopia.common.error.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Atomic per-account daily cap shared by Tutor, workflow, and Feynman model requests. */
@Service
public class TutorDailyRequestLimitService {

  static final int DAILY_LIMIT = 30;
  private static final ZoneId RESET_ZONE = ZoneId.of("Asia/Shanghai");
  private static final String RESERVE_SQL = """
      INSERT INTO tutor_daily_request_usage (user_id, usage_date, request_count, updated_at)
      VALUES (?, ?, 1, now())
      ON CONFLICT (user_id, usage_date) DO UPDATE
      SET request_count = tutor_daily_request_usage.request_count + 1,
          updated_at = now()
      WHERE tutor_daily_request_usage.request_count < ?
      RETURNING request_count
      """;

  private final JdbcTemplate jdbc;

  public TutorDailyRequestLimitService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Reserves one request before contacting the model; conversation IDs do not affect the cap. */
  public void reserve(Long userId) {
    List<Integer> counts =
        jdbc.query(
            RESERVE_SQL,
            (rs, rowNum) -> rs.getInt("request_count"),
            userId,
            LocalDate.now(RESET_ZONE),
            DAILY_LIMIT);
    if (counts.isEmpty()) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "TUTOR_DAILY_LIMIT_REACHED",
          "今日 AI 学习助手使用次数已达 30 次，请明天再试");
    }
  }

  @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Shanghai")
  public void removeExpiredUsage() {
    jdbc.update(
        "DELETE FROM tutor_daily_request_usage WHERE usage_date < ?",
        LocalDate.now(RESET_ZONE).minusDays(90));
  }
}
