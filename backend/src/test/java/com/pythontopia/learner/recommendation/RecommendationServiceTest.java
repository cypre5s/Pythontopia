package com.pythontopia.learner.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
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
 * P3.3：动态推送（孪生驱动）。验收（蓝图 AC 303 行）：改变洞察 → 推送结果改变（可断言），
 * 且推荐理由可溯源（绑定洞察 evidence event id）。
 *
 * <p>基线只有掌握度信号时按掌握度升序推送（最弱 KC 在前）；插入 error_pattern 洞察（命中掌握度排第三的
 * KC）后，该 KC 跃居首位且理由含「反复出错」、evidenceEventIds 指向真实 learning_event_log.id —— 证明
 * 输出随孪生洞察改变且可溯源（非假绿）。
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
  @Autowired LearnerInsightRepository insightRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void insightChangesRankingAndReasonCarriesTraceability() {
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
    String weakest = kcCodes.get(0);
    String strongerWeak = kcCodes.get(2);

    // 掌握度升序：weakest(0.10) < mid(0.20) < strongerWeak(0.30)。
    jdbcTemplate.update(seedMasterySql(), userId, kcCodes.get(0));
    jdbcTemplate.update(
        "INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence, last_event_at)"
            + " VALUES (?, ?, 0.200, 0.500, now())",
        userId, kcCodes.get(1));
    jdbcTemplate.update(
        "INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence, last_event_at)"
            + " VALUES (?, ?, 0.300, 0.500, now())",
        userId, kcCodes.get(2));

    // 基线：无洞察/记忆 → 按掌握度升序，最弱 KC 在前，理由不含「反复出错」。
    List<RecommendationDto> baseline = service.recommend(userId);
    assertThat(baseline).isNotEmpty();
    assertThat(baseline.get(0).kcCode()).isEqualTo(weakest);
    assertThat(baseline.get(0).reason()).doesNotContain("反复出错");
    assertThat(baseline.get(0).evidenceEventIds()).isEmpty();

    // 插入指向掌握度第三的 KC 的真实失败事件 + error_pattern 洞察（evidence 锚定该 event id）。
    Long eventId =
        jdbcTemplate.queryForObject(
            "INSERT INTO learning_event_log (user_id, event_type, payload, source, importance)"
                + " VALUES (?, 'practice_failed', ?::jsonb, 'backend', 4) RETURNING id",
            Long.class,
            userId,
            "{\"kcCode\":\"" + strongerWeak + "\"}");
    insightRepository.insert(
        userId,
        "在该知识点上反复出错",
        "error_pattern",
        List.of(strongerWeak),
        List.of(eventId),
        0.8,
        "TEST");

    // 改变洞察后：被洞察命中的 KC 跃居首位（即便其掌握度更高），理由含「反复出错」且 evidence 可溯源。
    List<RecommendationDto> afterInsight = service.recommend(userId);
    assertThat(afterInsight).isNotEmpty();
    RecommendationDto top = afterInsight.get(0);
    assertThat(top.kcCode()).isEqualTo(strongerWeak);
    assertThat(top.kcCode()).isNotEqualTo(weakest); // 排序确实因洞察改变
    assertThat(top.reason()).contains("反复出错");
    assertThat(top.evidenceEventIds()).contains(eventId);
  }

  private static String seedMasterySql() {
    return "INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence, last_event_at)"
        + " VALUES (?, ?, 0.100, 0.500, now())";
  }
}
