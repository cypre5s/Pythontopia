package com.pythontopia.bugmine.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.bugmine.dto.BugMineCardDto;
import com.pythontopia.bugmine.dto.BugMineCardType;
import com.pythontopia.bugmine.dto.BugMineSessionDto;
import com.pythontopia.bugmine.dto.BugMineSessionStatus;
import com.pythontopia.bugmine.dto.BugMineVariantProblemDto;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class BugMineRepository {

  private static final String SESSION_SELECT =
      """
      SELECT s.id, s.user_id, s.submission_id, s.problem_id, s.quest_id, s.status,
             s.retry_count, s.depth_layer, s.opened_at, s.closed_at,
             s.variant_problem_id, s.variant_submission_id, s.mastery_confirmed, s.profile_depth_target,
             vp.title AS variant_problem_title
      FROM bug_mine_session s
      LEFT JOIN course_problem vp ON vp.id = s.variant_problem_id
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public BugMineRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Long insertSession(
      Long userId,
      Long submissionId,
      Long problemId,
      Long questId,
      Long variantProblemId,
      int profileDepthTarget) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    try {
      jdbcTemplate.update(
          connection -> {
            PreparedStatement ps =
                connection.prepareStatement(
                    """
                    INSERT INTO bug_mine_session (
                      user_id, submission_id, problem_id, quest_id, status,
                      variant_problem_id, profile_depth_target
                    ) VALUES (?, ?, ?, ?, 'OPEN', ?, ?)
                    """,
                    new String[] {"id"});
            ps.setLong(1, userId);
            ps.setLong(2, submissionId);
            ps.setLong(3, problemId);
            if (questId == null) {
              ps.setNull(4, java.sql.Types.BIGINT);
            } else {
              ps.setLong(4, questId);
            }
            if (variantProblemId == null) {
              ps.setNull(5, java.sql.Types.BIGINT);
            } else {
              ps.setLong(5, variantProblemId);
            }
            ps.setInt(6, profileDepthTarget);
            return ps;
          },
          keyHolder);
    } catch (DuplicateKeyException existing) {
      return jdbcTemplate.queryForObject(
          "SELECT id FROM bug_mine_session WHERE submission_id = ?",
          Long.class,
          submissionId);
    }
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("bug_mine_session insert returned no key");
    }
    return key.longValue();
  }

  public Optional<Long> findSessionIdBySubmission(Long userId, Long submissionId) {
    return jdbcTemplate
        .query(
            "SELECT id FROM bug_mine_session WHERE user_id = ? AND submission_id = ?",
            (rs, rowNum) -> rs.getLong("id"),
            userId,
            submissionId)
        .stream()
        .findFirst();
  }

  public Optional<SessionContextRow> findSessionContext(
      Long userId, Long problemId, Long submissionId) {
    return jdbcTemplate
        .query(
            """
            SELECT COALESCE(km.mastery_score, 0.000) AS mastery_score,
                   COALESCE(profile_tags.tags, ARRAY[]::text[]) AS mistake_patterns,
                   EXISTS (
                     SELECT 1
                     FROM unnest(COALESCE(profile_tags.tags, ARRAY[]::text[])) AS tag(value)
                     WHERE tag.value <> ''
                       AND (
                         upper(tag.value) = COALESCE(jr.verdict, '')
                         OR lower(COALESCE(jr.failure_reason, '')) LIKE '%' || lower(tag.value) || '%'
                         OR lower(COALESCE(p.common_mistakes::text, '')) LIKE '%' || lower(tag.value) || '%'
                       )
                   ) AS pattern_matched
            FROM course_problem p
            LEFT JOIN kc_mastery km ON km.user_id = ? AND km.kc_code = p.kc_code
            LEFT JOIN judge_result jr ON jr.submission_id = ?
            LEFT JOIN LATERAL (
              SELECT ARRAY(
                SELECT DISTINCT tag
                FROM (
                  SELECT jsonb_array_elements_text(
                    CASE
                      WHEN jsonb_typeof(lp.dimensions #> '{error_pattern,tags}') = 'array'
                        THEN lp.dimensions #> '{error_pattern,tags}'
                      ELSE '[]'::jsonb
                    END
                  ) AS tag
                  UNION ALL
                  SELECT jsonb_array_elements_text(
                    CASE
                      WHEN jsonb_typeof(lp.error_patterns -> 'common_categories') = 'array'
                        THEN lp.error_patterns -> 'common_categories'
                      ELSE '[]'::jsonb
                    END
                  ) AS tag
                  UNION ALL
                  SELECT jsonb_array_elements_text(
                    CASE
                      WHEN jsonb_typeof(lp.error_patterns -> 'frequent_kc_codes') = 'array'
                        THEN lp.error_patterns -> 'frequent_kc_codes'
                      ELSE '[]'::jsonb
                    END
                  ) AS tag
                ) collected
                WHERE tag IS NOT NULL AND tag <> ''
                LIMIT 5
              ) AS tags
              FROM learner_profile lp
              WHERE lp.user_id = ?
            ) profile_tags ON TRUE
            WHERE p.id = ?
            """,
            (rs, rowNum) ->
                new SessionContextRow(
                    rs.getBigDecimal("mastery_score"),
                    readTextArray(rs, "mistake_patterns"),
                    rs.getBoolean("pattern_matched")),
            userId,
            submissionId,
            userId,
            problemId)
        .stream()
        .findFirst();
  }

  public void insertCard(Long sessionId, BugMineCardType type, JsonNode payload, boolean revealed) {
    jdbcTemplate.update(
        """
        INSERT INTO bug_mine_card (session_id, card_type, payload, revealed)
        VALUES (?, ?, ?::jsonb, ?)
        ON CONFLICT (session_id, card_type)
        DO UPDATE SET payload = EXCLUDED.payload, revealed = EXCLUDED.revealed
        """,
        sessionId,
        type.name(),
        writeJson(payload),
        revealed);
  }

  public Optional<BugMineSessionDto> findSession(Long userId, Long sessionId) {
    return jdbcTemplate
        .query(
            SESSION_SELECT + " WHERE s.id = ? AND s.user_id = ?",
            (rs, rowNum) -> mapSession(rs),
            sessionId,
            userId)
        .stream()
        .findFirst()
        .map(this::attachCards);
  }

  public Optional<BugMineSessionDto> findByIdInternal(Long sessionId) {
    return jdbcTemplate
        .query(
            SESSION_SELECT + " WHERE s.id = ?",
            (rs, rowNum) -> mapSession(rs),
            sessionId)
        .stream()
        .findFirst()
        .map(this::attachCards);
  }

  public List<BugMineCardDto> findCards(Long sessionId) {
    return jdbcTemplate.query(
        """
        SELECT id, session_id, card_type, payload, revealed, created_at
        FROM bug_mine_card
        WHERE session_id = ?
        ORDER BY card_type
        """,
        (rs, rowNum) -> mapCard(rs),
        sessionId);
  }

  public Optional<BugMineCardDto> findCard(Long sessionId, Long cardId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, session_id, card_type, payload, revealed, created_at
            FROM bug_mine_card
            WHERE id = ? AND session_id = ?
            """,
            (rs, rowNum) -> mapCard(rs),
            cardId,
            sessionId)
        .stream()
        .findFirst();
  }

  public void revealCard(Long sessionId, Long cardId) {
    jdbcTemplate.update(
        "UPDATE bug_mine_card SET revealed = TRUE WHERE id = ? AND session_id = ?",
        cardId,
        sessionId);
  }

  public void updateStatus(Long sessionId, BugMineSessionStatus status) {
    jdbcTemplate.update(
        """
        UPDATE bug_mine_session
        SET status = ?, closed_at = CASE WHEN ? = 'OPEN' THEN closed_at ELSE now() END
        WHERE id = ?
        """,
        status.name(),
        status.name(),
        sessionId);
  }

  public void updateVariantSubmission(Long sessionId, Long submissionId) {
    jdbcTemplate.update(
        "UPDATE bug_mine_session SET variant_submission_id = ? WHERE id = ?",
        submissionId,
        sessionId);
  }

  public void markMasteryConfirmed(Long sessionId) {
    jdbcTemplate.update(
        "UPDATE bug_mine_session SET mastery_confirmed = TRUE WHERE id = ?",
        sessionId);
  }

  public int incrementRetry(Long sessionId) {
    return jdbcTemplate.update(
        "UPDATE bug_mine_session SET retry_count = retry_count + 1 WHERE id = ?",
        sessionId);
  }

  public int bumpDepthLayer(Long sessionId, int layer) {
    return jdbcTemplate.update(
        """
        UPDATE bug_mine_session
        SET depth_layer = GREATEST(depth_layer, LEAST(5, GREATEST(1, ?)))
        WHERE id = ?
        """,
        layer,
        sessionId);
  }

  public int advanceDepthLayer(Long sessionId) {
    return jdbcTemplate.update(
        "UPDATE bug_mine_session SET depth_layer = LEAST(5, depth_layer + 1) WHERE id = ?",
        sessionId);
  }

  public List<ErrorBookRow> findOpenErrorBook(Long userId) {
    return jdbcTemplate.query(
        """
        SELECT s.id AS session_id,
               s.submission_id,
               s.problem_id,
               s.quest_id,
               s.status,
               s.retry_count,
               s.depth_layer,
               s.profile_depth_target,
               s.opened_at,
               s.variant_problem_id,
               vp.title AS variant_problem_title,
               p.kc_code,
               p.title AS problem_title,
               COALESCE(km.mastery_score, 0.000) AS mastery_score,
               COALESCE(profile_tags.tags, ARRAY[]::text[]) AS mistake_patterns,
               EXISTS (
                 SELECT 1
                 FROM unnest(COALESCE(profile_tags.tags, ARRAY[]::text[])) AS tag(value)
                 WHERE tag.value <> ''
                   AND (
                     upper(tag.value) = COALESCE(jr.verdict, '')
                     OR lower(COALESCE(jr.failure_reason, '')) LIKE '%' || lower(tag.value) || '%'
                     OR lower(COALESCE(p.common_mistakes::text, '')) LIKE '%' || lower(tag.value) || '%'
                   )
               ) AS pattern_matched
        FROM bug_mine_session s
        JOIN course_problem p ON p.id = s.problem_id
        LEFT JOIN course_problem vp ON vp.id = s.variant_problem_id
        LEFT JOIN kc_mastery km ON km.user_id = s.user_id AND km.kc_code = p.kc_code
        LEFT JOIN judge_result jr ON jr.submission_id = s.submission_id
        LEFT JOIN LATERAL (
          SELECT ARRAY(
            SELECT DISTINCT tag
            FROM (
              SELECT jsonb_array_elements_text(
                CASE
                  WHEN jsonb_typeof(lp.dimensions #> '{error_pattern,tags}') = 'array'
                    THEN lp.dimensions #> '{error_pattern,tags}'
                  ELSE '[]'::jsonb
                END
              ) AS tag
              UNION ALL
              SELECT jsonb_array_elements_text(
                CASE
                  WHEN jsonb_typeof(lp.error_patterns -> 'common_categories') = 'array'
                    THEN lp.error_patterns -> 'common_categories'
                  ELSE '[]'::jsonb
                END
              ) AS tag
              UNION ALL
              SELECT jsonb_array_elements_text(
                CASE
                  WHEN jsonb_typeof(lp.error_patterns -> 'frequent_kc_codes') = 'array'
                    THEN lp.error_patterns -> 'frequent_kc_codes'
                  ELSE '[]'::jsonb
                END
              ) AS tag
            ) collected
            WHERE tag IS NOT NULL AND tag <> ''
            LIMIT 5
          ) AS tags
          FROM learner_profile lp
          WHERE lp.user_id = s.user_id
        ) profile_tags ON TRUE
        WHERE s.user_id = ? AND s.status = 'OPEN'
        ORDER BY mastery_score ASC, pattern_matched DESC, s.opened_at DESC
        """,
        (rs, rowNum) ->
            new ErrorBookRow(
                rs.getLong("session_id"),
                rs.getLong("submission_id"),
                rs.getLong("problem_id"),
                (Long) rs.getObject("quest_id"),
                rs.getString("kc_code"),
                rs.getString("problem_title"),
                BugMineSessionStatus.valueOf(rs.getString("status")),
                rs.getInt("retry_count"),
                rs.getInt("depth_layer"),
                rs.getInt("profile_depth_target"),
                rs.getBigDecimal("mastery_score"),
                readTextArray(rs, "mistake_patterns"),
                rs.getBoolean("pattern_matched"),
                offset(rs, "opened_at"),
                (Long) rs.getObject("variant_problem_id"),
                rs.getString("variant_problem_title")),
        userId);
  }

  public int retryCount(Long sessionId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT retry_count FROM bug_mine_session WHERE id = ?",
            Integer.class,
            sessionId);
    return count == null ? 0 : count;
  }

  private BugMineSessionDto attachCards(BugMineSessionDto session) {
    List<BugMineCardDto> cards = findCards(session.id());
    return new BugMineSessionDto(
        session.id(),
        session.userId(),
        session.submissionId(),
        session.problemId(),
        session.questId(),
        session.status(),
        session.retryCount(),
        session.depthLayer(),
        session.profileDepthTarget(),
        session.masteryConfirmed(),
        session.variantSubmissionId(),
        session.openedAt(),
        session.closedAt(),
        session.variantProblem(),
        session.profileGuidance(),
        session.depthSteps(),
        cards);
  }

  private BugMineSessionDto mapSession(ResultSet rs) throws SQLException {
    Long variantProblemId = (Long) rs.getObject("variant_problem_id");
    BugMineVariantProblemDto variant =
        variantProblemId == null
            ? null
            : new BugMineVariantProblemDto(variantProblemId, rs.getString("variant_problem_title"));
    return new BugMineSessionDto(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getLong("submission_id"),
        rs.getLong("problem_id"),
        (Long) rs.getObject("quest_id"),
        BugMineSessionStatus.valueOf(rs.getString("status")),
        rs.getInt("retry_count"),
        rs.getInt("depth_layer"),
        rs.getInt("profile_depth_target"),
        rs.getBoolean("mastery_confirmed"),
        (Long) rs.getObject("variant_submission_id"),
        offset(rs, "opened_at"),
        offset(rs, "closed_at"),
        variant,
        null,
        List.of(),
        List.of());
  }

  private BugMineCardDto mapCard(ResultSet rs) throws SQLException {
    return new BugMineCardDto(
        rs.getLong("id"),
        rs.getLong("session_id"),
        BugMineCardType.valueOf(rs.getString("card_type")),
        readJson(rs.getString("payload")),
        rs.getBoolean("revealed"),
        offset(rs, "created_at"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }

  private List<String> readTextArray(ResultSet rs, String column) throws SQLException {
    Array array = rs.getArray(column);
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (raw instanceof String[] values) {
      return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).toList();
    }
    return List.of();
  }

  private String writeJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialise bug_mine_card payload", exception);
    }
  }

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to read bug_mine_card payload", exception);
    }
  }

  public record SessionContextRow(
      BigDecimal masteryScore, List<String> mistakePatterns, boolean patternMatched) {}

  public record ErrorBookRow(
      Long sessionId,
      Long submissionId,
      Long problemId,
      Long questId,
      String kcCode,
      String problemTitle,
      BugMineSessionStatus status,
      int retryCount,
      int depthLayer,
      int profileDepthTarget,
      BigDecimal masteryScore,
      List<String> mistakePatterns,
      boolean patternMatched,
      OffsetDateTime openedAt,
      Long variantProblemId,
      String variantProblemTitle) {}
}
