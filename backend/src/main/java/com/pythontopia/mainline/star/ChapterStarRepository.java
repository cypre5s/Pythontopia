package com.pythontopia.mainline.star;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.mainline.domain.ChapterStar;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** chapter_star 唯一权威读写 + star_award_event 审计写入。 */
@Repository
public class ChapterStarRepository {

  private static final String SELECT_COLUMNS =
      "id, user_id, chapter, stars, best_test_score, breakdown::text AS breakdown, updated_at";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ChapterStarRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<ChapterStar> findByUser(long userId) {
    return jdbcTemplate.query(
        "SELECT " + SELECT_COLUMNS + " FROM chapter_star WHERE user_id = ?", this::map, userId);
  }

  public Optional<ChapterStar> find(long userId, int chapter) {
    return jdbcTemplate
        .query(
            "SELECT " + SELECT_COLUMNS + " FROM chapter_star WHERE user_id = ? AND chapter = ?",
            this::map,
            userId,
            chapter)
        .stream()
        .findFirst();
  }

  /**
   * 幂等只升写入：stars 取 {@code GREATEST(existing, new)}（只升不降，§5），best_test_score 同理（null-safe），
   * breakdown 直接覆盖为最新构成（调用方已合并 recovered）。
   */
  public void upsert(long userId, int chapter, int stars, Integer bestTestScore, String breakdownJson) {
    jdbcTemplate.update(
        """
        INSERT INTO chapter_star (user_id, chapter, stars, best_test_score, breakdown, updated_at)
        VALUES (?, ?, ?, ?, ?::jsonb, now())
        ON CONFLICT (user_id, chapter) DO UPDATE SET
          stars = GREATEST(chapter_star.stars, EXCLUDED.stars),
          best_test_score = GREATEST(
            COALESCE(chapter_star.best_test_score, -1), COALESCE(EXCLUDED.best_test_score, -1)),
          breakdown = EXCLUDED.breakdown,
          updated_at = now()
        """,
        userId,
        chapter,
        stars,
        bestTestScore,
        breakdownJson == null || breakdownJson.isBlank() ? "{}" : breakdownJson);
  }

  /** 星级变更审计（每次有效 delta 一条），教师端按本表聚合。 */
  public void insertEvent(long userId, int chapter, int delta, String source, String reason) {
    jdbcTemplate.update(
        "INSERT INTO star_award_event (user_id, chapter, delta, source, reason)"
            + " VALUES (?, ?, ?, ?, ?)",
        userId,
        chapter,
        delta,
        source,
        reason);
  }

  private ChapterStar map(ResultSet rs, int rowNum) throws SQLException {
    return new ChapterStar(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getInt("chapter"),
        rs.getInt("stars"),
        (Integer) rs.getObject("best_test_score"),
        readJson(rs.getString("breakdown")),
        offset(rs, "updated_at"));
  }

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException ex) {
      return objectMapper.createObjectNode();
    }
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
