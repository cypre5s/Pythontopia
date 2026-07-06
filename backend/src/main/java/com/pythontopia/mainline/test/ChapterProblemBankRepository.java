package com.pythontopia.mainline.test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 读取 packId=43 本章真实题库，供章节测评优先复用编程题。 */
@Repository
public class ChapterProblemBankRepository {

  private static final long PACK_ID = 43L;

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public ChapterProblemBankRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  public List<PreferredCodingQuestion> findByKcCodes(List<String> kcCodes) {
    if (kcCodes == null || kcCodes.isEmpty()) {
      return List.of();
    }
    List<ProblemRow> rows =
        jdbcClient
            .sql(
                """
                SELECT id, kc_code, title, statement_markdown,
                       samples::text AS samples,
                       hidden_test_cases::text AS hidden_test_cases,
                       reference_solution_code
                FROM course_problem
                WHERE pack_id = :packId
                  AND kc_code IN (:kcCodes)
                  AND reference_solution_code IS NOT NULL
                  AND btrim(reference_solution_code) <> ''
                ORDER BY id
                """)
            .param("packId", PACK_ID)
            .param("kcCodes", kcCodes)
            .query(
                (rs, rowNum) ->
                    new ProblemRow(
                        rs.getLong("id"),
                        rs.getString("kc_code"),
                        rs.getString("title"),
                        rs.getString("statement_markdown"),
                        rs.getString("samples"),
                        rs.getString("hidden_test_cases"),
                        rs.getString("reference_solution_code")))
            .list();

    List<PreferredCodingQuestion> result = new ArrayList<>();
    for (ProblemRow row : rows) {
      List<CodingCase> samples = readCases(row.samples());
      if (samples.isEmpty()) {
        continue;
      }
      List<CodingCase> hidden = readCases(row.hiddenTestCases());
      if (hidden.isEmpty()) {
        hidden = List.copyOf(samples);
      }
      result.add(
          new PreferredCodingQuestion(
              row.id(),
              row.title(),
              row.statementMarkdown(),
              samples,
              hidden,
              row.referenceSolution(),
              row.kcCode()));
    }
    return List.copyOf(result);
  }

  private List<CodingCase> readCases(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    try {
      JsonNode array = objectMapper.readTree(raw);
      if (!array.isArray()) {
        return List.of();
      }
      List<CodingCase> cases = new ArrayList<>();
      for (JsonNode item : array) {
        if (item.hasNonNull("input") && item.hasNonNull("output")) {
          cases.add(new CodingCase(item.path("input").asText(), item.path("output").asText()));
        }
      }
      return List.copyOf(cases);
    } catch (JsonProcessingException ex) {
      return List.of();
    }
  }

  public record CodingCase(
      @JsonProperty("input") String input, @JsonProperty("output") String output) {}

  public record PreferredCodingQuestion(
      @JsonProperty("source_problem_id") long sourceProblemId,
      @JsonProperty("title") String title,
      @JsonProperty("statement_markdown") String statementMarkdown,
      @JsonProperty("samples") List<CodingCase> samples,
      @JsonProperty("hidden_test_cases") List<CodingCase> hiddenTestCases,
      @JsonProperty("reference_solution") String referenceSolution,
      @JsonProperty("kc_code") String kcCode) {}

  private record ProblemRow(
      long id,
      String kcCode,
      String title,
      String statementMarkdown,
      String samples,
      String hiddenTestCases,
      String referenceSolution) {}
}
