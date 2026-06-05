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
            SELECT id, pack_id, kc_code, title, statement_markdown, input_description,
                   output_description, difficulty, samples
            FROM course_problem
            WHERE pack_id = ? AND id = ?
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
            SELECT samples, hidden_test_cases
            FROM course_problem
            WHERE id = ?
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
                WHERE p.kc_code = ? AND p.id <> ?
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
            SELECT id, title
            FROM course_problem
            WHERE kc_code = ? AND id <> ?
            ORDER BY id
            LIMIT 1
            """,
            (rs, rowNum) -> new VariantProblem(rs.getLong("id"), rs.getString("title")),
            kcCode,
            problemId)
        .stream()
        .findFirst();
  }

  public record VariantProblem(Long id, String title) {}

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
