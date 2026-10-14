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

  @Test
  void chapterOneKeepsTheFourIndependentKnowledgeRootsFromPack43() {
    Integer chapterOneRoots =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM course_kc WHERE pack_id = 43 AND chapter_order = 1"
                + " AND cardinality(prerequisite_codes) = 0",
            Integer.class);

    assertThat(chapterOneRoots).isEqualTo(4);
    Boolean computationalThinkingUsesItsSourcePrerequisite =
        jdbcTemplate.queryForObject(
            "SELECT 'PY43-KC-004' = ANY(prerequisite_codes) FROM course_kc"
                + " WHERE pack_id = 43 AND kc_code = 'PY43-KC-003'",
            Boolean.class);

    assertThat(computationalThinkingUsesItsSourcePrerequisite).isTrue();
  }

  @Test
  void coursePackKcPageRefsLinkPagesSoRagGetsKcCodes() {
    // R-14 S6-B：RAG loader 用 `p.id = ANY(kc.page_refs)` 把 KC 贴到课件页（见
    // services/pythontopia-rag/.../data/loader.py）。若 page_refs 缺失/悬挂（曾出现的
    // chunks=0 根因之一），该 join 为空 → 索引页 kc_codes 全空 → 按 KC 检索全 NO_EVIDENCE。
    // 这里在 Testcontainers 真库上断言该数据链路非空且无悬挂，作为 RAG 检索的永久回归守门。
    Integer kcWithPageRefs =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM course_kc WHERE pack_id = 43 AND cardinality(page_refs) > 0",
            Integer.class);
    assertThat(kcWithPageRefs).isNotNull().isGreaterThanOrEqualTo(60);

    Integer pagesLinkedToKc =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT p.id)
            FROM course_page p
            JOIN course_kc kc ON kc.pack_id = p.pack_id AND p.id = ANY(kc.page_refs)
            WHERE p.pack_id = 43
            """,
            Integer.class);
    assertThat(pagesLinkedToKc).isNotNull().isGreaterThan(50);

    Integer danglingPageRefs =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM course_kc kc
            CROSS JOIN LATERAL unnest(kc.page_refs) ref(page_id)
            LEFT JOIN course_page p ON p.id = ref.page_id AND p.pack_id = kc.pack_id
            WHERE kc.pack_id = 43 AND p.id IS NULL
            """,
            Integer.class);
    assertThat(danglingPageRefs).isZero();

    // 具体可观测样例：KC-012「基础函数」(input/print) 必须能 join 到课件页。
    Integer kc012Pages =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT p.id)
            FROM course_page p
            JOIN course_kc kc ON kc.pack_id = p.pack_id AND p.id = ANY(kc.page_refs)
            WHERE p.pack_id = 43 AND kc.kc_code = 'PY43-KC-012'
            """,
            Integer.class);
    assertThat(kc012Pages).isNotNull().isGreaterThanOrEqualTo(1);
  }

  private void assertCount(String tableName, int expected) {
    Integer actual =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE pack_id = 43", Integer.class);
    assertThat(actual).isEqualTo(expected);
  }
}
