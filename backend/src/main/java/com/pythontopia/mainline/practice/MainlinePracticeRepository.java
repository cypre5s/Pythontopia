package com.pythontopia.mainline.practice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code mainline_practice_question} 题库（seed 静态表）只读 + {@code mainline_stage_progress.questions_correct}
 * 「练-客观题」进度读写仓库。
 *
 * <ul>
 *   <li>读题走「带答案内部模型」{@link MainlinePracticeQuestion}（{@code options_json} / {@code answer_json}
 *       由 {@link ObjectMapper} 解析为 {@code List<String>}）；下发题面再经 {@link MainlinePracticeQuestion#toDto()}
 *       剥离答案。
 *   <li>{@code questions_correct} 写路径为<b>幂等并集 UPSERT</b>（{@code DISTINCT unnest(old || new)}），
 *       与 {@code pages_read} / {@code examples_done} 同构；无该用户该 stage 行时随 ON CONFLICT 先 INSERT。
 * </ul>
 *
 * <p>注意：本仓库只触碰 {@code questions_correct} 列，其余进度列（视频 / 课件 / 例题 / 完成时间）仍由
 * {@code MainlineStageProgressRepository}（P3）权威读写，互不越界。
 */
@Repository
public class MainlinePracticeRepository {

  private static final String SELECT_COLUMNS =
      "id, chapter, stage_index, ordinal, kc_code, type, stem, options_json, answer_json,"
          + " blank_count, explanation, source_label";

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public MainlinePracticeRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  /** 某小节全部客观题（带答案，按 ordinal 升序）。题库未 seed 时返回空集。 */
  public List<MainlinePracticeQuestion> findByStage(int chapter, int stageIndex) {
    return jdbcClient
        .sql(
            "SELECT "
                + SELECT_COLUMNS
                + " FROM mainline_practice_question"
                + " WHERE chapter = :chapter AND stage_index = :stageIndex"
                + " AND required_for_stage = TRUE"
                + " ORDER BY ordinal")
        .param("chapter", chapter)
        .param("stageIndex", stageIndex)
        .query(this::map)
        .list();
  }

  /** All objective questions for a KC, including supplemental rows excluded from stage completion. */
  public List<MainlinePracticeQuestion> findForBossFallback(int chapter, String kcCode) {
    return jdbcClient
        .sql(
            "SELECT "
                + SELECT_COLUMNS
                + " FROM mainline_practice_question"
                + " WHERE chapter = :chapter AND kc_code = :kcCode"
                + " ORDER BY required_for_stage DESC, stage_index, ordinal")
        .param("chapter", chapter)
        .param("kcCode", kcCode)
        .query(this::map)
        .list();
  }

  /** 单题（带答案），供判分与归属校验。 */
  public Optional<MainlinePracticeQuestion> findById(long questionId) {
    return jdbcClient
        .sql("SELECT " + SELECT_COLUMNS + " FROM mainline_practice_question WHERE id = :id")
        .param("id", questionId)
        .query(this::map)
        .optional();
  }

  /** 某用户某 stage 已答对的客观题 id 集合（去重，无行返回空集）。 */
  public List<Long> findCorrectQuestionIds(long userId, long stageId) {
    return jdbcClient
        .sql(
            "SELECT questions_correct FROM mainline_stage_progress"
                + " WHERE user_id = :userId AND stage_id = :stageId")
        .param("userId", userId)
        .param("stageId", stageId)
        .query((rs, rowNum) -> readLongList(rs.getArray("questions_correct")))
        .optional()
        .orElse(List.of());
  }

  /**
   * 把某题 id 并集追加进 {@code questions_correct}（幂等）。无该用户该 stage 行时由 ON CONFLICT 先 INSERT
   * 再并集去重；其余进度列走表默认值，不与 P3 的写路径冲突。
   */
  public void appendCorrect(long userId, long stageId, long questionId) {
    jdbcClient
        .sql(
            """
            INSERT INTO mainline_stage_progress (user_id, stage_id, questions_correct, updated_at)
            VALUES (:userId, :stageId, ARRAY[:questionId]::bigint[], now())
            ON CONFLICT (user_id, stage_id) DO UPDATE SET
              questions_correct = ARRAY(
                SELECT DISTINCT unnest(
                  mainline_stage_progress.questions_correct || EXCLUDED.questions_correct)),
              updated_at = now()
            """)
        .param("userId", userId)
        .param("stageId", stageId)
        .param("questionId", questionId)
        .update();
  }

  private MainlinePracticeQuestion map(ResultSet rs, int rowNum) throws SQLException {
    return new MainlinePracticeQuestion(
        rs.getLong("id"),
        rs.getInt("chapter"),
        rs.getInt("stage_index"),
        rs.getInt("ordinal"),
        rs.getString("kc_code"),
        PracticeQuestionType.valueOf(rs.getString("type")),
        rs.getString("stem"),
        parseJsonStringArray(rs.getString("options_json")),
        parseJsonStringArray(rs.getString("answer_json")),
        rs.getInt("blank_count"),
        rs.getString("explanation"),
        rs.getString("source_label"));
  }

  /** JSONB 文本 → {@code List<String>}（容错：非数组返回空集，元素按 {@code asText} 取值）。 */
  private List<String> parseJsonStringArray(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      JsonNode node = objectMapper.readTree(json);
      if (!node.isArray()) {
        return List.of();
      }
      List<String> out = new ArrayList<>(node.size());
      for (JsonNode element : node) {
        out.add(element.asText(""));
      }
      return out;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("题库 JSON 解析失败: " + json, e);
    }
  }

  private static List<Long> readLongList(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    List<Long> out = new ArrayList<>();
    if (raw instanceof Object[] values) {
      for (Object value : values) {
        if (value instanceof Number number) {
          out.add(number.longValue());
        }
      }
    }
    return out;
  }
}
