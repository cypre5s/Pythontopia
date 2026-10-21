package com.pythontopia.feynman;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeynmanBugBankRepository {

  private static final TypeReference<List<Integer>> LINE_INDICES = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public FeynmanBugBankRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public Optional<BugSample> findOne(String kcCode, String difficulty) {
    return jdbc
        .query(
            """
            UPDATE feynman_bug_bank
            SET use_count = use_count + 1
            WHERE id = (
              SELECT id FROM feynman_bug_bank WHERE kc_code = ?
              ORDER BY (difficulty = ?) DESC, use_count, id LIMIT 1
            )
            RETURNING misconception_tag, buggy_code, fixed_code,
                      bug_line_indices::text AS bug_line_indices,
                      hint_md, difficulty
            """,
            (rs, rowNum) ->
                new BugSample(
                    rs.getString("misconception_tag"),
                    rs.getString("buggy_code"),
                    rs.getString("fixed_code"),
                    readLineIndices(rs.getString("bug_line_indices")),
                    rs.getString("hint_md"),
                    rs.getString("difficulty")),
            kcCode,
            difficulty)
        .stream()
        .findFirst();
  }

  public boolean insertIfAbsent(
      String kcCode, String topic, FeynmanGraphClient.BugExposure sample) {
    return jdbc.update(
            """
            INSERT INTO feynman_bug_bank (
              kc_code, topic, difficulty, misconception_tag, buggy_code, fixed_code,
              bug_line_indices, hint_md, sample_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (sample_hash) DO NOTHING
            """,
            kcCode,
            topic,
            sample.difficulty(),
            sample.misconceptionTag(),
            sample.buggyCode(),
            sample.fixedCode(),
            writeLineIndices(sample.bugLineIndices()),
            sample.hintMd(),
            hash(kcCode, topic, sample))
        == 1;
  }

  private List<Integer> readLineIndices(String json) {
    try {
      return objectMapper.readValue(json, LINE_INDICES);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Feynman bug line indices are invalid", exception);
    }
  }

  private String writeLineIndices(List<Integer> indices) {
    try {
      return objectMapper.writeValueAsString(indices);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Feynman bug line indices", exception);
    }
  }

  private static String hash(String kcCode, String topic, FeynmanGraphClient.BugExposure sample) {
    String key =
        kcCode
            + "\u0000"
            + topic
            + "\u0000"
            + sample.difficulty()
            + "\u0000"
            + sample.misconceptionTag()
            + "\u0000"
            + sample.buggyCode()
            + "\u0000"
            + sample.fixedCode();
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public record BugSample(
      String misconceptionTag,
      String buggyCode,
      String fixedCode,
      List<Integer> bugLineIndices,
      String hintMd,
      String difficulty) {}
}
