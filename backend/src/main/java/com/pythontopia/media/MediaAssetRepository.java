package com.pythontopia.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.media.domain.MediaAsset;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** {@code media_asset} 表的 JDBC 访问层（对齐 {@code ResourceRunRecorder} 的写法）。 */
@Repository
public class MediaAssetRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public MediaAssetRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public MediaAsset insert(
      Long userId,
      MediaAssetKind kind,
      String prompt,
      String filePath,
      String mimeType,
      boolean degraded,
      String source,
      List<String> kcCodes,
      Object meta) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO media_asset (
                    user_id, kind, prompt, file_path, mime_type,
                    degraded, source, kc_codes, meta
                  )
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                  """,
                  new String[] {"id"});
          if (userId == null) {
            ps.setNull(1, java.sql.Types.BIGINT);
          } else {
            ps.setLong(1, userId);
          }
          ps.setString(2, kind.name());
          ps.setString(3, prompt);
          ps.setString(4, filePath);
          ps.setString(5, mimeType);
          ps.setBoolean(6, degraded);
          ps.setString(7, source);
          Array kcArray =
              connection.createArrayOf(
                  "TEXT", (kcCodes == null ? List.<String>of() : kcCodes).toArray(String[]::new));
          ps.setArray(8, kcArray);
          ps.setString(9, writeJson(meta));
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    Long id = key == null ? null : key.longValue();
    return new MediaAsset(
        id,
        userId,
        kind,
        prompt,
        filePath,
        mimeType,
        degraded,
        source,
        kcCodes == null ? List.of() : List.copyOf(kcCodes),
        Instant.now());
  }

  public Optional<MediaAsset> findById(long id) {
    List<MediaAsset> rows =
        jdbcTemplate.query(
            """
            SELECT id, user_id, kind, prompt, file_path, mime_type,
                   degraded, source, kc_codes, created_at
            FROM media_asset WHERE id = ?
            """,
            MAPPER,
            id);
    return rows.stream().findFirst();
  }

  private static final RowMapper<MediaAsset> MAPPER =
      (ResultSet rs, int rowNum) -> {
        Array kcArray = rs.getArray("kc_codes");
        List<String> kcCodes =
            kcArray == null ? List.of() : List.of((String[]) kcArray.getArray());
        Long userId = rs.getObject("user_id") == null ? null : rs.getLong("user_id");
        return new MediaAsset(
            rs.getLong("id"),
            userId,
            MediaAssetKind.valueOf(rs.getString("kind")),
            rs.getString("prompt"),
            rs.getString("file_path"),
            rs.getString("mime_type"),
            rs.getBoolean("degraded"),
            rs.getString("source"),
            kcCodes,
            rs.getTimestamp("created_at").toInstant());
      };

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? objectMapper.createObjectNode() : value);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialize media asset meta", ex);
    }
  }
}
