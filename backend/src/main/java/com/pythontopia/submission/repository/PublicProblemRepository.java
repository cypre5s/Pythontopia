package com.pythontopia.submission.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.submission.dto.PublicProblemDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicProblemRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public PublicProblemRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Optional<PublicProblemDto> findPublic(Long packId, Long problemId) {
    return jdbcTemplate
        .query(
            """
            SELECT p.id, p.pack_id, p.kc_code, p.title, p.statement_markdown, p.input_description,
                   p.output_description, p.difficulty, p.samples
            FROM course_problem p
            WHERE p.pack_id = ? AND p.id = ? AND NOT EXISTS (
              SELECT 1 FROM course_resource_state s WHERE s.pack_id = p.pack_id
                AND s.resource_type = 'PROBLEM' AND s.resource_id = p.id AND NOT s.enabled)
            """,
            (rs, rowNum) -> map(rs),
            packId,
            problemId)
        .stream()
        .findFirst();
  }

  public Optional<JudgeTestCases> findTestCases(Long problemId) {
    return jdbcTemplate
        .query(
            """
            SELECT p.samples, p.hidden_test_cases
            FROM course_problem p
            WHERE p.id = ? AND p.pack_id = 43 AND NOT EXISTS (
              SELECT 1 FROM course_resource_state s WHERE s.pack_id = p.pack_id
                AND s.resource_type = 'PROBLEM' AND s.resource_id = p.id AND NOT s.enabled)
            """,
            (rs, rowNum) -> new JudgeTestCases(
                readJson(rs.getString("samples")),
                readJson(rs.getString("hidden_test_cases"))),
            problemId)
        .stream()
        .findFirst();
  }

  public String findKcCode(Long problemId) {
    return jdbcTemplate.queryForObject(
        "SELECT kc_code FROM course_problem WHERE id = ?", String.class, problemId);
  }

  public Optional<RepresentativeProblem> findRepresentativeProblem(Long packId, String kcCode) {
    if (kcCode == null || kcCode.isBlank()) {
      return Optional.empty();
    }
    return jdbcTemplate
        .query(
            """
            SELECT p.id, p.difficulty
            FROM course_problem p
            WHERE p.pack_id = ? AND p.kc_code = ? AND NOT EXISTS (
              SELECT 1 FROM course_resource_state s WHERE s.pack_id = p.pack_id
                AND s.resource_type = 'PROBLEM' AND s.resource_id = p.id AND NOT s.enabled)
              ORDER BY
              CASE
                WHEN jsonb_typeof(p.hidden_test_cases) = 'array' THEN
                  CASE WHEN jsonb_array_length(p.hidden_test_cases) > 0 THEN 0 ELSE 1 END
                ELSE 1
              END,
              p.id
            LIMIT 1
            """,
            (rs, rowNum) -> new RepresentativeProblem(rs.getLong("id"), rs.getString("difficulty")),
            packId,
            kcCode)
        .stream()
        .findFirst();
  }

  public Optional<VariantProblem> findVariantProblem(Long userId, Long problemId, String kcCode) {
    if (kcCode == null || kcCode.isBlank()) {
      return Optional.empty();
    }
    Optional<VariantProblem> fresh =
        jdbcTemplate
            .query(
                """
                SELECT p.id, p.title
                FROM course_problem p
                WHERE p.pack_id = 43 AND p.kc_code = ? AND p.id <> ? AND NOT EXISTS (
                  SELECT 1 FROM course_resource_state s WHERE s.pack_id = p.pack_id
                    AND s.resource_type = 'PROBLEM' AND s.resource_id = p.id AND NOT s.enabled)
                  AND NOT EXISTS (
                    SELECT 1
                    FROM practice_submission ps
                    WHERE ps.user_id = ?
                      AND ps.problem_id = p.id
                      AND ps.status = 'PASSED'
                  )
                ORDER BY p.id
                LIMIT 1
                """,
                (rs, rowNum) -> new VariantProblem(rs.getLong("id"), rs.getString("title")),
                kcCode,
                problemId,
                userId)
            .stream()
            .findFirst();
    if (fresh.isPresent()) {
      return fresh;
    }
    return jdbcTemplate
        .query(
            """
            SELECT p.id, p.title
            FROM course_problem p
            WHERE p.pack_id = 43 AND p.kc_code = ? AND p.id <> ? AND NOT EXISTS (
              SELECT 1 FROM course_resource_state s WHERE s.pack_id = p.pack_id
                AND s.resource_type = 'PROBLEM' AND s.resource_id = p.id AND NOT s.enabled)
            ORDER BY p.id
            LIMIT 1
            """,
            (rs, rowNum) -> new VariantProblem(rs.getLong("id"), rs.getString("title")),
            kcCode,
            problemId)
        .stream()
        .findFirst();
  }

  public record VariantProblem(Long id, String title) {}

  public record RepresentativeProblem(Long id, String difficulty) {}

  private PublicProblemDto map(ResultSet rs) throws SQLException {
    return new PublicProblemDto(
        rs.getLong("id"),
        rs.getLong("pack_id"),
        rs.getString("kc_code"),
        rs.getString("title"),
        rs.getString("statement_markdown"),
        rs.getString("input_description"),
        rs.getString("output_description"),
        rs.getString("difficulty"),
        readJson(rs.getString("samples")));
  }

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createArrayNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to read course_problem json", exception);
    }
  }

  public record JudgeTestCases(JsonNode samples, JsonNode hiddenTestCases) {}
}
