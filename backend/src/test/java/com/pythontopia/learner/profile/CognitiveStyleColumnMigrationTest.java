package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.profile.dto.CognitiveStyleDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * learner_profile.cognitive_style 必须是 JSONB。
 *
 * <p>V012 建表时是 VARCHAR(64)，而 ProfileRepository 一直按 {@code ?::jsonb} 写 CognitiveStyleDto：固定
 * 开销就占掉 42 字符，两个 double 只剩 22 字符预算。ProfileAgent 一旦返回 0.3333333333333333 这类高精度
 * 小数，INSERT 就会因 {@code value too long for type character varying(64)} 失败，整场访谈回滚、学生看到 500。
 */
class CognitiveStyleColumnMigrationTest {

  private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

  @Test
  void highPrecisionCognitiveStyleOverflowsTheLegacyVarcharBudget() throws Exception {
    String payload =
        new ObjectMapper()
            .writeValueAsString(new CognitiveStyleDto(0.3333333333333333, 0.6666666666666666));

    // 正是这个长度让 VARCHAR(64) 写入失败——所以列必须放宽成 JSONB。
    assertThat(payload.length()).isGreaterThan(64);
  }

  @Test
  void migrationWidensCognitiveStyleToJsonb() throws IOException {
    String sql = readMigration("V186__learner_profile_cognitive_style_jsonb.sql");

    assertThat(normalize(sql))
        .contains("ALTER TABLE learner_profile ALTER COLUMN cognitive_style TYPE JSONB");
  }

  @Test
  void migrationDropsAndRecreatesTheDependentView() throws IOException {
    String sql = normalize(readMigration("V186__learner_profile_cognitive_style_jsonb.sql"));

    // PostgreSQL 拒绝 ALTER 视图依赖的列，必须先 DROP 再按原样重建（同 V113 的做法）。
    assertThat(sql).contains("DROP VIEW IF EXISTS game_bootstrap_view");
    assertThat(sql).contains("CREATE VIEW game_bootstrap_view AS");
    assertThat(sql.indexOf("DROP VIEW IF EXISTS game_bootstrap_view"))
        .isLessThan(sql.indexOf("ALTER TABLE learner_profile"));
    assertThat(sql.indexOf("ALTER TABLE learner_profile"))
        .isLessThan(sql.indexOf("CREATE VIEW game_bootstrap_view AS"));
  }

  @Test
  void recreatedViewKeepsEveryColumnOfTheOriginal() throws IOException {
    String original = normalize(readMigration("V113__player_direction_eight_way.sql"));
    String migrated = normalize(readMigration("V186__learner_profile_cognitive_style_jsonb.sql"));

    String originalBody = original.substring(original.indexOf("CREATE VIEW game_bootstrap_view AS"));
    String migratedBody = migrated.substring(migrated.indexOf("CREATE VIEW game_bootstrap_view AS"));
    assertThat(migratedBody).isEqualTo(originalBody);
  }

  private static String readMigration(String fileName) throws IOException {
    Path path = MIGRATIONS.resolve(fileName);
    assertThat(Files.exists(path)).as("migration %s must exist", fileName).isTrue();
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  /** 去掉注释与多余空白，只比较可执行语句。 */
  private static String normalize(String sql) {
    StringBuilder statements = new StringBuilder();
    for (String line : sql.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("--")) {
        continue;
      }
      statements.append(trimmed).append(' ');
    }
    return statements.toString().replaceAll("\\s+", " ").trim();
  }
}
