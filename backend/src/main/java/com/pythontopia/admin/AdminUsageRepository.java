package com.pythontopia.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUsageRepository {

  private static final String EVENTS =
      """
      WITH events AS (
        SELECT l.id, 'LOOP_RUN'::text AS event_type, l.user_id, u.username,
               ('学习闭环 #' || l.id)::text AS label, l.created_at AS occurred_at,
               '已记录'::text AS status, l.input_tokens, l.output_tokens
          FROM loop_run l JOIN users u ON u.id = l.user_id
        UNION ALL
        SELECT r.id, 'RESOURCE_RUN', r.user_id, u.username, r.scenario,
               r.created_at, r.status, NULL::integer, NULL::integer
          FROM resource_run r JOIN users u ON u.id = r.user_id
        UNION ALL
        SELECT m.id, 'MEDIA_JOB', m.user_id, COALESCE(u.username, '已注销账号'), m.kind,
               m.created_at, m.status, NULL::integer, NULL::integer
          FROM media_job m LEFT JOIN users u ON u.id = m.user_id
        UNION ALL
        SELECT s.id, 'FORMAL_JUDGE', s.user_id, u.username, p.title,
               s.submitted_at, COALESCE(j.verdict, s.status), NULL::integer, NULL::integer
          FROM practice_submission s
          JOIN users u ON u.id = s.user_id
          JOIN course_problem p ON p.id = s.problem_id
          LEFT JOIN judge_result j ON j.submission_id = s.id
      )
      """;

  private final JdbcTemplate jdbc;

  public AdminUsageRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public AdminUsageDto list(int days, String username) {
    String filter =
        " WHERE occurred_at >= now() - (? * interval '1 day')"
            + " AND (CAST(? AS text) IS NULL OR position(lower(CAST(? AS text)) in lower(username)) > 0)";
    Summary summary =
        jdbc.queryForObject(
            EVENTS
                + " SELECT count(*) AS total_events,"
                + " COALESCE(sum(input_tokens), 0) AS input_tokens,"
                + " COALESCE(sum(output_tokens), 0) AS output_tokens,"
                + " count(*) FILTER (WHERE input_tokens IS NULL OR output_tokens IS NULL) AS unknown_events"
                + " FROM events"
                + filter,
            (rs, row) ->
                new Summary(
                    rs.getLong("total_events"),
                    rs.getLong("input_tokens"),
                    rs.getLong("output_tokens"),
                    rs.getLong("unknown_events")),
            days,
            username,
            username);
    List<AdminUsageDto.Event> content =
        jdbc.query(
            EVENTS
                + " SELECT id, event_type, user_id, username, label, occurred_at, status, input_tokens, output_tokens"
                + " FROM events"
                + filter
                + " ORDER BY occurred_at DESC, event_type, id DESC LIMIT 500",
            this::map,
            days,
            username,
            username);
    return new AdminUsageDto(
        content,
        days,
        summary.totalEvents(),
        summary.inputTokens(),
        summary.outputTokens(),
        summary.unknownEvents());
  }

  private AdminUsageDto.Event map(ResultSet rs, int row) throws SQLException {
    return new AdminUsageDto.Event(
        rs.getLong("id"),
        rs.getString("event_type"),
        rs.getLong("user_id"),
        rs.getString("username"),
        rs.getString("label"),
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getString("status"),
        (Integer) rs.getObject("input_tokens"),
        (Integer) rs.getObject("output_tokens"));
  }

  private record Summary(long totalEvents, long inputTokens, long outputTokens, long unknownEvents) {}
}
