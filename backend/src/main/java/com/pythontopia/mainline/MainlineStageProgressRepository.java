package com.pythontopia.mainline;

import com.pythontopia.mainline.domain.MainlineStageProgress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * mainline_stage_progress 读写仓库。所有写路径都是<b>幂等 UPSERT</b>（§9「所有写接口幂等」）：
 *
 * <ul>
 *   <li>视频：{@code video_watched_sec += clamp(delta)}（delta 已在服务层钳制 + 限频）。
 *   <li>课件页 / 例题：数组并集去重（{@code DISTINCT unnest(old || new)}），重复打点不改状态。
 * </ul>
 */
@Repository
public class MainlineStageProgressRepository {

  private static final String SELECT_COLUMNS =
      "id, user_id, stage_id, video_watched_sec, pages_read, examples_done, completed_at, updated_at";

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

  /** 视频时长累加（幂等：delta 已钳制 + 限频；此处只做单行原子累加）。 */
  public MainlineStageProgress addVideoSeconds(long userId, long stageId, int deltaSec) {
    jdbcTemplate.update(
        """
        INSERT INTO mainline_stage_progress (user_id, stage_id, video_watched_sec, updated_at)
        VALUES (?, ?, ?, now())
        ON CONFLICT (user_id, stage_id) DO UPDATE SET
          video_watched_sec = mainline_stage_progress.video_watched_sec + EXCLUDED.video_watched_sec,
          updated_at = now()
        """,
        userId,
        stageId,
        deltaSec);
    return find(userId, stageId).orElseThrow();
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
        offset(rs, "completed_at"),
        offset(rs, "updated_at"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
