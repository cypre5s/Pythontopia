package com.pythontopia.stage08;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.PythontopiaApplication;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Stage08PathBoardAcceptanceIntegrationTest {

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
        "pythontopia.security.jwt.secret",
        () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
    registry.add("pythontopia.services.graphUrl", () -> "http://graph.test");
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper objectMapper;
  @Autowired RestTemplate graphRestTemplate;

  @Test
  void planPersistsPathAndQuestsThenStudentCompletesFirstQuest() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("path-stu01");
    Long userId = userId("path-stu01");
    seedProfile(userId);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/path/plan"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphSuccessResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> planResponse =
        restTemplate.exchange(
            "/api/learner/path/plan", HttpMethod.POST, bearer(token, null), Map.class);
    assertThat(planResponse.getStatusCode())
        .as("planning response: %s", planResponse.getBody())
        .isEqualTo(HttpStatus.OK);
    Number generatedQuestCount = (Number) planResponse.getBody().get("generatedQuestCount");
    assertThat(generatedQuestCount.intValue()).isEqualTo(3);

    Integer activePaths =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM learning_path WHERE user_id = ? AND status = 'ACTIVE'",
            Integer.class,
            userId);
    assertThat(activePaths).isEqualTo(1);

    Integer nodes =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM learning_path_node lp JOIN learning_path p ON lp.path_id = p.id WHERE p.user_id = ?",
            Integer.class,
            userId);
    assertThat(nodes).isEqualTo(3);

    Integer quests =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quest WHERE user_id = ? AND source = 'PATH_PLANNER'",
            Integer.class,
            userId);
    assertThat(quests).isEqualTo(3);

    Integer correctlyBoundQuests =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM quest q
            JOIN learning_path_node n ON n.id = q.source_ref_id
            WHERE q.user_id = ? AND q.source = 'PATH_PLANNER'
              AND q.kc_code = n.kc_code AND q.node_type = n.node_type
            """,
            Integer.class,
            userId);
    assertThat(correctlyBoundQuests).isEqualTo(3);

    ResponseEntity<List> today =
        restTemplate.exchange(
            "/api/quests/today", HttpMethod.GET, bearer(token, null), List.class);
    assertThat(today.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(today.getBody()).isNotEmpty();
    Map<String, Object> firstQuest = (Map<String, Object>) today.getBody().get(0);
    Long firstQuestId = ((Number) firstQuest.get("id")).longValue();
    Long resourceId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO learning_resource (
              user_id, pack_id, kc_codes, type, title, content_format, content
            ) VALUES (?, 43, ARRAY['PY43-KC-001']::text[], 'HANDOUT', 'stage08-resource',
              'MARKDOWN', '{}'::jsonb) RETURNING id
            """,
            Long.class,
            userId);
    ResponseEntity<Map> consumed =
        restTemplate.exchange(
            "/api/workshop/resources/" + resourceId + "/consumptions",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(consumed.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> started =
        restTemplate.exchange(
            "/api/quests/" + firstQuestId + "/start",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(started.getBody().get("status")).isEqualTo("IN_PROGRESS");

    ResponseEntity<Map> completed =
        restTemplate.exchange(
            "/api/quests/" + firstQuestId + "/complete",
            HttpMethod.POST,
            bearer(token, Map.of("resourceId", resourceId)),
            Map.class);
    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(completed.getBody().get("status")).isEqualTo("COMPLETED");

    Integer consumedFacts =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM learning_event_log
            WHERE user_id = ?
              AND event_type = 'resource_consumed'
              AND payload ->> 'kcCode' = 'PY43-KC-001'
            """,
            Integer.class,
            userId);
    assertThat(consumedFacts).isGreaterThanOrEqualTo(1);

    Integer directReadRewards =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM kc_mastery_event
            WHERE user_id = ?
              AND kc_code = 'PY43-KC-001'
              AND source = 'RESOURCE_READ'
            """,
            Integer.class,
            userId);
    assertThat(directReadRewards).isZero();
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void planRequiresProfile() {
    String token = registerAndLogin("path-stu02");
    ResponseEntity<Map> planResponse =
        restTemplate.exchange(
            "/api/learner/path/plan", HttpMethod.POST, bearer(token, null), Map.class);
    assertThat(planResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(planResponse.getBody().get("code")).isEqualTo("PROFILE_REQUIRED");
  }

  @Test
  void crossUserAccessReturns404OrForbiddenForQuest() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String stuA = registerAndLogin("path-stu03");
    Long aId = userId("path-stu03");
    seedProfile(aId);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/path/plan"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphSuccessResponse(), MediaType.APPLICATION_JSON));
    restTemplate.exchange(
        "/api/learner/path/plan", HttpMethod.POST, bearer(stuA, null), Map.class);

    Long otherQuestId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM quest WHERE user_id = ? ORDER BY id LIMIT 1",
            Long.class,
            aId);
    String stuB = registerAndLogin("path-stu04");
    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/quests/" + otherQuestId + "/start",
            HttpMethod.POST,
            bearer(stuB, null),
            Map.class);
    assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    graphServer.verify(Duration.ofSeconds(2));
  }

  private String graphSuccessResponse() throws Exception {
    Map<String, Object> body =
        Map.of(
            "user_id", 1,
            "pack_id", 43,
            "goal_summary", "本周聚焦 PY43-KC-001, PY43-KC-002",
            "nodes",
                List.of(
                    Map.of(
                        "day_index", 1,
                        "order_in_day", 1,
                        "kc_code", "PY43-KC-001",
                        "node_type", "READ_RESOURCE",
                        "reason_summary", "先补讲义",
                        "evidence_summary", "evidence",
                        "suggested_resource_type", "HANDOUT"),
                    Map.of(
                        "day_index", 7,
                        "order_in_day", 1,
                        "kc_code", "PY43-KC-002",
                        "node_type", "FIX_BUG",
                        "reason_summary", "回顾画像",
                        "evidence_summary", "ok"),
                    Map.of(
                        "day_index", 1,
                        "order_in_day", 2,
                        "kc_code", "PY43-KC-003",
                        "node_type", "SOLVE_PROBLEM",
                        "reason_summary", "练习 1 题",
                        "evidence_summary", "evidence")),
            "safety_result", Map.of("passed", true, "reasons", List.of()),
            "grounding_result", Map.of("passed", true, "reasons", List.of(), "unknown_kc", List.of()),
            "deterministic_signature", "stage08-test",
            "status", "SUCCESS");
    return objectMapper.writeValueAsString(body);
  }

  private Long userId(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username = ?", Long.class, username);
  }

  private void seedProfile(Long userId) {
    jdbcTemplate.update(
        """
        INSERT INTO learner_profile (
          user_id, pack_id, major, base_level, learning_goal, cognitive_style,
          learning_preference, error_patterns, practice_capability,
          emotion_persistence, narrative_summary
        ) VALUES (?, 43, 'CS',
          '{"programming_years":1,"languages_known":["C"],"python_self_rated_level":2}'::jsonb,
          '{"kind":"EXAM","deadline_iso":null,"target_kc_codes":["PY43-KC-001"]}'::jsonb,
          '{"field_dependent":0.4,"sequential_vs_holistic":0.5}'::jsonb,
          '{"media_priority":["code","text"],"session_minutes_per_day":45}'::jsonb,
          '{"frequent_kc_codes":["PY43-KC-001"],"common_categories":["syntax"]}'::jsonb,
          '{"avg_attempts_per_problem":2.0,"success_rate":0.65}'::jsonb,
          '{"resilience":0.7,"recent_mood":"neutral"}'::jsonb,
          '测试画像')
        """,
        userId);
  }

  private String registerAndLogin(String username) {
    restTemplate.postForEntity(
        "/api/auth/register",
        Map.of(
            "username", username,
            "password", "P@ssw0rd1",
            "displayName", username,
            "role", "STUDENT"),
        Map.class);
    ResponseEntity<Map> response =
        restTemplate.postForEntity(
            "/api/auth/login",
            Map.of("username", username, "password", "P@ssw0rd1"),
            Map.class);
    return (String) response.getBody().get("token");
  }

  private HttpEntity<?> bearer(String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(body, headers);
  }
}
