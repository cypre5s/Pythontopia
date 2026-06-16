package com.pythontopia.media;

import com.pythontopia.media.domain.MediaJob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** {@code media_job} 表（V158）的 JDBC 访问层（对齐 {@link MediaAssetRepository} 的写法）。 */
@Repository
public class MediaJobRepository {

  private final JdbcTemplate jdbcTemplate;

  public MediaJobRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 以 {@code PENDING} 入库一条新作业，返回带自增 id 的记录。 */
  public MediaJob insert(Long userId, String kind, String fingerprint, String specJson) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO media_job (user_id, kind, status, fingerprint, spec_json)
                  VALUES (?, ?, 'PENDING', ?, ?::jsonb)
                  """,
                  new String[] {"id"});
          if (userId == null) {
            ps.setNull(1, java.sql.Types.BIGINT);
          } else {
            ps.setLong(1, userId);
          }
          ps.setString(2, kind);
          ps.setString(3, fingerprint);
          ps.setString(4, specJson == null ? "{}" : specJson);
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    return findById(key == null ? -1L : key.longValue())
        .orElseThrow(() -> new IllegalStateException("media_job insert returned no row"));
  }

  /** 更新状态（及成功时的 {@code asset_id} / 失败时的 {@code error_code}），刷新 {@code updated_at}。 */
  public void updateStatus(long id, MediaJobStatus status, Long assetId, String errorCode) {
    jdbcTemplate.update(
        """
        UPDATE media_job
        SET status = ?, asset_id = ?, error_code = ?, updated_at = now()
        WHERE id = ?
        """,
        ps -> {
          ps.setString(1, status.name());
          if (assetId == null) {
            ps.setNull(2, java.sql.Types.BIGINT);
          } else {
            ps.setLong(2, assetId);
          }
          ps.setString(3, errorCode);
          ps.setLong(4, id);
        });
  }

  /** 逐镜回写实时进度（jsonb），刷新 {@code updated_at}。供 SSE / 轮询读取。 */
  public void updateProgress(long id, String progressJson) {
    jdbcTemplate.update(
        "UPDATE media_job SET progress_json = ?::jsonb, updated_at = now() WHERE id = ?",
        progressJson == null ? "{}" : progressJson,
        id);
  }

  public Optional<MediaJob> findById(long id) {
    List<MediaJob> rows =
        jdbcTemplate.query(
            """
            SELECT id, user_id, kind, status, fingerprint, spec_json, progress_json,
                   asset_id, error_code, created_at, updated_at
            FROM media_job WHERE id = ?
            """,
            MAPPER,
            id);
    return rows.stream().findFirst();
  }

  /**
   * 幂等命中查询：找该用户同一 {@code fingerprint} 已成功且仍挂着成片的最新作业。
   * 命中则上层复用成片、不再重复合成（高并发护栏）。
   */
  public Optional<MediaJob> findCachedSuccess(Long userId, String fingerprint) {
    List<MediaJob> rows =
        jdbcTemplate.query(
            """
            SELECT id, user_id, kind, status, fingerprint, spec_json, progress_json,
                   asset_id, error_code, created_at, updated_at
            FROM media_job
            WHERE user_id = ? AND fingerprint = ? AND status = 'SUCCESS' AND asset_id IS NOT NULL
            ORDER BY created_at DESC
            LIMIT 1
            """,
            MAPPER,
            userId,
            fingerprint);
    return rows.stream().findFirst();
  }

  private static final RowMapper<MediaJob> MAPPER =
      (ResultSet rs, int rowNum) -> {
        Long userId = rs.getObject("user_id") == null ? null : rs.getLong("user_id");
        Long assetId = rs.getObject("asset_id") == null ? null : rs.getLong("asset_id");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new MediaJob(
            rs.getLong("id"),
            userId,
            rs.getString("kind"),
            MediaJobStatus.valueOf(rs.getString("status")),
            rs.getString("fingerprint"),
            rs.getString("spec_json"),
            rs.getString("progress_json"),
            assetId,
            rs.getString("error_code"),
            rs.getTimestamp("created_at").toInstant(),
            updatedAt == null ? null : updatedAt.toInstant());
      };
}
