package com.pythontopia.learner.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.twin.LearnerTwinService;
import java.math.BigDecimal;
import java.util.List;
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

/**
 * P3.3：动态推送与 Twin/Expedition 共用同一 KC 选择顺序，洞察只补充可溯源理由。
 *
 * <p>掌握度、到期复习、先修条件与近期结果只由共享规则决定；AI 洞察和证据图可以解释推荐，但不能另行重排 KC。
 */
@Testcontainers
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecommendationServiceTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add(
        "pythontopia.security.jwt.secret", () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
    registry.add("pythontopia.services.graphUrl", () -> "http://graph.test");
    registry.add("pythontopia.judge.python", () -> "python3");
  }

  @Autowired LearnerRecommendationService service;
  @Autowired LearnerTwinService twinService;
  @Autowired LearnerInsightRepository insightRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void bootstrapOnlyMasteryIsNotRecommendedAsAProvenWeakness() {
    Long userId =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES ('rec-bootstrap-stu', 'rec bootstrap', 'x', 'STUDENT') RETURNING id",
            Long.class);
    String kcCode =
        jdbcTemplate.queryForObject(
            "SELECT kc_code FROM course_kc WHERE pack_id = 43 ORDER BY kc_code LIMIT 1",
            String.class);
    jdbcTemplate.update(
        "INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence) VALUES (?, ?, 0, 0)",
        userId,
        kcCode);
    jdbcTemplate.update(
        "INSERT INTO kc_mastery_event (user_id, kc_code, source, delta, reason)"
            + " VALUES (?, ?, 'BOOTSTRAP', 0, 'profile bootstrap')",
        userId,
        kcCode);

    List<RecommendationDto> recommendations = service.recommend(userId);

    assertThat(recommendations).isNotEmpty();
    assertThat(recommendations.get(0).reason()).contains("尚未评估");
    assertThat(recommendations.get(0).reason()).doesNotContain("掌握度较低");
  }

  @Test
  void insightAddsTraceabilityWithoutChangingSharedSelectionOrder() {
    Long userId =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES ('rec-stu', 'rec', 'x', 'STUDENT') RETURNING id",
            Long.class);

    List<String> kcCodes =
        jdbcTemplate.queryForList(
            "SELECT kc_code FROM course_kc WHERE pack_id = 43 ORDER BY kc_code LIMIT 3",
            String.class);
    assertThat(kcCodes).hasSize(3);
    // 掌握度升序：weakest(0.10) < mid(0.20) < strongerWeak(0.30)。
    seedMastery(userId, kcCodes.get(0), new BigDecimal("0.100"));
    seedMastery(userId, kcCodes.get(1), new BigDecimal("0.200"));
    seedMastery(userId, kcCodes.get(2), new BigDecimal("0.300"));

    // 基线：无洞察/记忆 → 按掌握度升序，最弱 KC 在前，理由不含「反复出错」。
    List<RecommendationDto> baseline = service.recommend(userId);
    assertThat(baseline).isNotEmpty();
    List<String> expectedOrder = twinService.frontier(userId, null, List.of(), 3).picks().stream()
        .map(pick -> pick.kcCode()).toList();
    assertThat(baseline).extracting(RecommendationDto::kcCode).containsExactlyElementsOf(expectedOrder);
    assertThat(baseline.get(0).reason()).doesNotContain("反复出错");
    assertThat(baseline.get(0).evidenceEventIds()).isEmpty();
    assertThat(baseline.get(0).checkIds()).isEmpty();
    assertThat(baseline.get(0).pageIds()).isEmpty();

    String insightKc = baseline.get(0).kcCode();
    // 插入指向共享策略首选 KC 的真实失败事件 + error_pattern 洞察。
    Long eventId =
        jdbcTemplate.queryForObject(
            "INSERT INTO learning_event_log (user_id, event_type, payload, source, importance)"
                + " VALUES (?, 'practice_failed', ?::jsonb, 'backend', 4) RETURNING id",
            Long.class,
            userId,
            "{\"kcCode\":\"" + insightKc + "\"}");
    insightRepository.insert(
        userId,
        "在该知识点上反复出错",
        "error_pattern",
        List.of(insightKc),
        List.of(eventId),
        0.8,
        "TEST");

    // 洞察提供理由和证据，但不改变 Twin/Expedition 的 KC 优先级。
    List<RecommendationDto> afterInsight = service.recommend(userId);
    assertThat(afterInsight).isNotEmpty();
    assertThat(afterInsight).extracting(RecommendationDto::kcCode).containsExactlyElementsOf(expectedOrder);
    RecommendationDto top = afterInsight.get(0);
    assertThat(top.reason()).contains("反复出错");
    assertThat(top.evidenceEventIds()).contains(eventId);
    assertThat(top.checkIds()).isEmpty();
    assertThat(top.pageIds()).isEmpty();
  }

  @Test
  void evidenceMemorySignalChangesRankingBeforeRawMemoryFallback() {
    Long userId =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES ('rec-evidence-stu', 'rec evidence', 'x', 'STUDENT') RETURNING id",
            Long.class);

    List<String> kcCodes =
        jdbcTemplate.queryForList(
            "SELECT kc_code FROM course_kc WHERE pack_id = 43 ORDER BY kc_code LIMIT 3",
            String.class);
    seedMastery(userId, kcCodes.get(0), new BigDecimal("0.100"));
    seedMastery(userId, kcCodes.get(1), new BigDecimal("0.200"));
    seedMastery(userId, kcCodes.get(2), new BigDecimal("0.300"));

    List<RecommendationDto> baseline = service.recommend(userId);
    assertThat(baseline).isNotEmpty();
    String evidenceWeak = baseline.getLast().kcCode();
    List<String> baselineOrder = baseline.stream().map(RecommendationDto::kcCode).toList();

    Long resourceId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO learning_resource (
              user_id, pack_id, kc_codes, type, title, content_format, content, evidence_page_ids,
              safety_status, grounding_status
            ) VALUES (?, 43, ARRAY[?]::text[], 'HANDOUT', '证据记忆讲义',
              'STRUCT_JSON', '{}'::jsonb, ARRAY[10707]::bigint[], 'PASSED', 'PASSED') RETURNING id
            """,
            Long.class,
            userId,
            evidenceWeak);
    Long eventId =
        jdbcTemplate.queryForObject(
            "INSERT INTO learning_event_log (user_id, event_type, payload, source, importance)"
                + " VALUES (?, 'practice_failed', ?::jsonb, 'backend', 5) RETURNING id",
            Long.class,
            userId,
            "{\"kcCode\":\"" + evidenceWeak + "\",\"problemId\":476}");
    Long checkId =
        jdbcTemplate.queryForObject(
        """
        INSERT INTO resource_learning_check (
          user_id, resource_id, kc_code, evidence_type, answer_text, operation_evidence,
          status, mastery_delta, remediation_type, reason
        ) VALUES (?, ?, ?, 'MICRO_QUIZ', '没答到关键点', '{}'::jsonb,
          'FAILED', -0.040, 'PRACTICE', 'recommendation-evidence-memory') RETURNING id
        """,
            Long.class,
        userId,
        resourceId,
        evidenceWeak);

    List<RecommendationDto> recommendations = service.recommend(userId);

    assertThat(recommendations).isNotEmpty();
    assertThat(recommendations).extracting(RecommendationDto::kcCode).containsExactlyElementsOf(baselineOrder);
    RecommendationDto evidenceRecommendation = recommendations.stream()
        .filter(item -> item.kcCode().equals(evidenceWeak)).findFirst().orElseThrow();
    assertThat(evidenceRecommendation.reason()).contains("学习证据图", "微测失败");
    assertThat(evidenceRecommendation.evidenceEventIds()).contains(eventId);
    assertThat(evidenceRecommendation.checkIds()).contains(checkId);
    assertThat(evidenceRecommendation.pageIds()).contains(10707L);
  }

  private void seedMastery(Long userId, String kcCode, BigDecimal masteryScore) {
    jdbcTemplate.update(
        "INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence, last_event_at)"
            + " VALUES (?, ?, ?, 0.500, now())",
        userId,
        kcCode,
        masteryScore);
    jdbcTemplate.update(
        "INSERT INTO kc_mastery_event (user_id, kc_code, source, delta, applied_delta, reason)"
            + " VALUES (?, ?, 'PRACTICE_PASS', 0, 0, 'verified recommendation fixture')",
        userId,
        kcCode);
  }

}
