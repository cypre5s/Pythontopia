package com.pythontopia.mainline;

import com.pythontopia.mainline.domain.MainlineStageProgress;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * mainline_stage_progress 读写仓库。所有写路径都是<b>幂等 UPSERT</b>（§9「所有写接口幂等」）：
 *
 * <ul>
 *   <li>视频：单次计入秒数不超过两次服务端上报间隔（并受服务层单次上限和限频约束）。
 *   <li>课件页 / 例题：数组并集去重（{@code DISTINCT unnest(old || new)}），重复打点不改状态。
 * </ul>
 */
@Repository
public class MainlineStageProgressRepository {

  private static final String SELECT_COLUMNS =
      "id, user_id, stage_id, video_watched_sec, pages_read, examples_done, questions_correct,"
          + " completed_at, updated_at";

  private final JdbcTemplate jdbcTemplate;

  public MainlineStageProgressRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 某学生全部 stage 进度（overview 单查询的动态半边，按 user_id 一次取齐，无 N+1）。 */
  public List<MainlineStageProgress> findByUser(long userId) {
    return jdbcTemplate.query(
        "SELECT " + SELECT_COLUMNS + " FROM mainline_stage_progress WHERE user_id = ?",
        this::map,
        userId);
  }

  public Optional<MainlineStageProgress> find(long userId, long stageId) {
    return jdbcTemplate
        .query(
            "SELECT "
                + SELECT_COLUMNS
                + " FROM mainline_stage_progress WHERE user_id = ? AND stage_id = ?",
            this::map,
            userId,
            stageId)
        .stream()
        .findFirst();
  }

  /** 视频时长累加；首个请求只建立服务端计时基准，后续请求受真实服务端间隔限制。 */
  public MainlineStageProgress addVideoSeconds(long userId, long stageId, int deltaSec) {
    jdbcTemplate.update(
        """
        INSERT INTO mainline_stage_progress (
          user_id, stage_id, video_watched_sec, video_last_reported_at, updated_at)
        VALUES (?, ?, 0, now(), now())
        ON CONFLICT (user_id, stage_id) DO UPDATE SET
          video_watched_sec = mainline_stage_progress.video_watched_sec + LEAST(
            ?,
            GREATEST(0, FLOOR(EXTRACT(EPOCH FROM (
              now() - COALESCE(mainline_stage_progress.video_last_reported_at, now())
            )))::int)
          ),
          video_last_reported_at = now(),
          updated_at = now()
        """,
        userId,
        stageId,
        deltaSec);
    return find(userId, stageId).orElseThrow();
  }

  /** Atomically claim the single learning event for completed video progress. */
  public boolean claimVideoConsumed(long userId, long stageId) {
    return jdbcTemplate.update(
            "UPDATE mainline_stage_progress SET video_consumed_at = now()"
                + " WHERE user_id = ? AND stage_id = ? AND video_consumed_at IS NULL",
            userId,
            stageId)
        > 0;
  }

  /** Atomically claim the single learning event for completed courseware progress. */
  public boolean claimCoursewareConsumed(long userId, long stageId) {
    return jdbcTemplate.update(
            "UPDATE mainline_stage_progress SET courseware_consumed_at = now()"
                + " WHERE user_id = ? AND stage_id = ? AND courseware_consumed_at IS NULL",
            userId,
            stageId)
        > 0;
  }

  /** 课件页阅读打点：并集去重（幂等）。 */
  public MainlineStageProgress addPageRead(long userId, long stageId, long pageId) {
    jdbcTemplate.update(
        """
        INSERT INTO mainline_stage_progress (user_id, stage_id, pages_read, updated_at)
        VALUES (?, ?, ARRAY[?]::bigint[], now())
        ON CONFLICT (user_id, stage_id) DO UPDATE SET
          pages_read = ARRAY(
            SELECT DISTINCT unnest(mainline_stage_progress.pages_read || EXCLUDED.pages_read)),
          updated_at = now()
        """,
        userId,
        stageId,
        pageId);
    return find(userId, stageId).orElseThrow();
  }

  /**
   * 课件页批量阅读打点：并集去重（幂等）。本小节 KC 关联页一次上报，{@code pageIds} 去重后作为 bigint[]
   * 并集合并；空集合也走 UPSERT（确保行存在，状态不变）。
   */
  public MainlineStageProgress addPagesRead(long userId, long stageId, List<Long> pageIds) {
    List<Long> distinct =
        pageIds == null
            ? List.of()
            : pageIds.stream().filter(Objects::nonNull).distinct().toList();
    jdbcTemplate.update(
        con -> {
          PreparedStatement ps =
              con.prepareStatement(
                  """
                  INSERT INTO mainline_stage_progress (user_id, stage_id, pages_read, updated_at)
                  VALUES (?, ?, ?, now())
                  ON CONFLICT (user_id, stage_id) DO UPDATE SET
                    pages_read = ARRAY(
                      SELECT DISTINCT unnest(
                        mainline_stage_progress.pages_read || EXCLUDED.pages_read)),
                    updated_at = now()
                  """);
          ps.setLong(1, userId);
          ps.setLong(2, stageId);
          ps.setArray(3, con.createArrayOf("bigint", distinct.toArray()));
          return ps;
        });
    return find(userId, stageId).orElseThrow();
  }

  /** 例题完成打点：并集去重（幂等）。 */
  public MainlineStageProgress addExampleDone(long userId, long stageId, long problemId) {
    jdbcTemplate.update(
        """
        INSERT INTO mainline_stage_progress (user_id, stage_id, examples_done, updated_at)
        VALUES (?, ?, ARRAY[?]::bigint[], now())
        ON CONFLICT (user_id, stage_id) DO UPDATE SET
          examples_done = ARRAY(
            SELECT DISTINCT unnest(mainline_stage_progress.examples_done || EXCLUDED.examples_done)),
          updated_at = now()
        """,
        userId,
        stageId,
        problemId);
    return find(userId, stageId).orElseThrow();
  }

  /**
   * 仅当三件套全过线且尚未标记完成时，原子置 {@code completed_at = now()}。
   *
   * @return true 表示「本次新近完成」（用于触发一次星级重算）；false 表示已完成或仍未达标。
   */
  public boolean markCompleted(long userId, long stageId) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE mainline_stage_progress
            SET completed_at = now(), updated_at = now()
            WHERE user_id = ? AND stage_id = ? AND completed_at IS NULL
            """,
            userId,
            stageId);
    return updated == 1;
  }

  private MainlineStageProgress map(ResultSet rs, int rowNum) throws SQLException {
    return new MainlineStageProgress(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getLong("stage_id"),
        rs.getInt("video_watched_sec"),
        MainlineStageRepository.readLongArray(rs.getArray("pages_read")),
        MainlineStageRepository.readLongArray(rs.getArray("examples_done")),
        MainlineStageRepository.readLongArray(rs.getArray("questions_correct")),
        offset(rs, "completed_at"),
        offset(rs, "updated_at"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
