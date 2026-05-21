package com.pythontopia.coursepack;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
import com.pythontopia.coursepack.service.CoursePackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class CoursePackServiceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("pythontopia.security.jwt.secret", () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
  }

  @Autowired CoursePackService service;

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void seedCountsMatchPack43AcceptanceNumbers() {
    assertThat(service.getActivePack().id()).isEqualTo(43L);

    assertCount("course_document", 7);
    assertCount("course_page", 561);
    assertCount("course_chapter", 7);
    assertCount("course_kc", 65);
    assertCount("course_example", 46);
    assertCount("course_problem", 39);
  }

  @Test
  void seedPagesAreNotPlaceholdersAndKcPrerequisitesAreValid() {
    Integer longPageCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM course_page WHERE pack_id = 43 AND length(content_markdown) > 50",
            Integer.class);
    assertThat(longPageCount).isNotNull().isGreaterThanOrEqualTo(533);

    Integer danglingPrerequisites =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM course_kc kc
            CROSS JOIN LATERAL unnest(kc.prerequisite_codes) prereq(code)
            LEFT JOIN course_kc target ON target.kc_code = prereq.code AND target.pack_id = kc.pack_id
            WHERE kc.pack_id = 43 AND target.id IS NULL
            """,
            Integer.class);
    assertThat(danglingPrerequisites).isZero();
  }

  private void assertCount(String tableName, int expected) {
    Integer actual =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE pack_id = 43", Integer.class);
    assertThat(actual).isEqualTo(expected);
  }
}
