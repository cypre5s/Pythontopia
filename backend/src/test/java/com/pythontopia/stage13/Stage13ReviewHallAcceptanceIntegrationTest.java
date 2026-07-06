package com.pythontopia.stage13;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.PythontopiaApplication;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.client.ExpectedCount;
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
class Stage13ReviewHallAcceptanceIntegrationTest {

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
    registry.add("pythontopia.judge.python", () -> "python3");
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper objectMapper;
  @Autowired RestTemplate graphRestTemplate;

  @Test
  void computeAcceptKeepFlowExercisesAllMetricsAndPathTransitions() throws Exception {
    MockRestServiceServer graphServer =
        MockRestServiceServer.bindTo(graphRestTemplate).ignoreExpectOrder(true).build();

    String token = registerAndLogin("eval-stu01");
    Long userId = userId("eval-stu01");
    seedProfile(userId);
    seedActivity(userId);

    String narrative = narrativePayload();
    String pathPlan = pathPlannerPayload();

    graphServer
        .expect(ExpectedCount.times(2),
            MockRestRequestMatchers.requestTo("http://graph.test/agents/evaluation/narrative"))
        .andRespond(MockRestResponseCreators.withSuccess(narrative, MediaType.APPLICATION_JSON));
    graphServer
        .expect(ExpectedCount.times(2),
            MockRestRequestMatchers.requestTo("http://graph.test/agents/path/plan"))
        .andRespond(MockRestResponseCreators.withSuccess(pathPlan, MediaType.APPLICATION_JSON));

    ResponseEntity<Map> create =
        restTemplate.exchange(
            "/api/learner/evaluations",
            HttpMethod.POST,
            bearer(token, Map.of("periodDays", 7)),
            Map.class);
    assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long evaluationId = ((Number) create.getBody().get("id")).longValue();

    Integer metricsCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM learner_evaluation_metric WHERE evaluation_id = ?",
            Integer.class,
            evaluationId);
    assertThat(metricsCount).isEqualTo(6);

    @SuppressWarnings("unchecked")
    Map<String, Object> draftPath = (Map<String, Object>) create.getBody().get("draftPath");
    assertThat(draftPath).isNotNull();
    assertThat(draftPath.get("status")).isEqualTo("DRAFT");
    Long draftPathId = ((Number) draftPath.get("id")).longValue();

    Integer evalRevisions =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profile_revision WHERE user_id = ? AND source = 'EVALUATION'",
            Integer.class,
            userId);
    assertThat(evalRevisions).isEqualTo(1);

    ResponseEntity<Map> accept =
        restTemplate.exchange(
            "/api/learner/evaluations/" + evaluationId + "/accept-new-path",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(accept.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(accept.getBody().get("actedStatus")).isEqualTo("ACCEPTED");

    String draftStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM learning_path WHERE id = ?", String.class, draftPathId);
    assertThat(draftStatus).isEqualTo("ACTIVE");

    ResponseEntity<Map> second =
        restTemplate.exchange(
            "/api/learner/evaluations",
            HttpMethod.POST,
            bearer(token, Map.of("periodDays", 7)),
            Map.class);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long secondId = ((Number) second.getBody().get("id")).longValue();
    @SuppressWarnings("unchecked")
    Map<String, Object> secondDraft = (Map<String, Object>) second.getBody().get("draftPath");
    Long secondDraftId = ((Number) secondDraft.get("id")).longValue();

    ResponseEntity<Map> keep =
        restTemplate.exchange(
            "/api/learner/evaluations/" + secondId + "/keep-old",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(keep.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(keep.getBody().get("actedStatus")).isEqualTo("KEPT_OLD");

    String secondDraftStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM learning_path WHERE id = ?", String.class, secondDraftId);
    assertThat(secondDraftStatus).isEqualTo("ARCHIVED");

    String firstActivePathStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM learning_path WHERE id = ?", String.class, draftPathId);
    assertThat(firstActivePathStatus).isEqualTo("ACTIVE");

    graphServer.verify(Duration.ofSeconds(5));
  }

  @Test
  void crossUserEvaluationAccessIsForbidden() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String tokenA = registerAndLogin("eval-stu02");
    Long userA = userId("eval-stu02");
    seedProfile(userA);
    seedActivity(userA);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/evaluation/narrative"))
        .andRespond(MockRestResponseCreators.withSuccess(narrativePayload(), MediaType.APPLICATION_JSON));
    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/path/plan"))
        .andRespond(MockRestResponseCreators.withSuccess(pathPlannerPayload(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> create =
        restTemplate.exchange(
            "/api/learner/evaluations",
            HttpMethod.POST,
            bearer(tokenA, Map.of("periodDays", 7)),
            Map.class);
    Long evaluationId = ((Number) create.getBody().get("id")).longValue();

    String tokenB = registerAndLogin("eval-stu03");
    ResponseEntity<Map> blocked =
        restTemplate.exchange(
            "/api/learner/evaluations/" + evaluationId,
            HttpMethod.GET,
            bearer(tokenB, null),
            Map.class);
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void rejectsRepeatedAccept() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("eval-stu04");
    Long userId = userId("eval-stu04");
    seedProfile(userId);
    seedActivity(userId);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/evaluation/narrative"))
        .andRespond(MockRestResponseCreators.withSuccess(narrativePayload(), MediaType.APPLICATION_JSON));
    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/path/plan"))
        .andRespond(MockRestResponseCreators.withSuccess(pathPlannerPayload(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> create =
        restTemplate.exchange(
            "/api/learner/evaluations",
            HttpMethod.POST,
            bearer(token, Map.of("periodDays", 7)),
            Map.class);
    Long evaluationId = ((Number) create.getBody().get("id")).longValue();

    restTemplate.exchange(
        "/api/learner/evaluations/" + evaluationId + "/accept-new-path",
        HttpMethod.POST,
        bearer(token, null),
        Map.class);

    ResponseEntity<Map> second =
        restTemplate.exchange(
            "/api/learner/evaluations/" + evaluationId + "/accept-new-path",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(second.getBody().get("code")).isEqualTo("EVALUATION_ALREADY_ACTED");
    graphServer.verify(Duration.ofSeconds(2));
  }

  private void seedProfile(Long userId) {
    jdbcTemplate.update(
        """
        INSERT INTO learner_profile (
          user_id, pack_id, major, base_level, learning_goal, cognitive_style,
          learning_preference, error_patterns, practice_capability,
          emotion_persistence, narrative_summary
        ) VALUES (
          ?, 43, 'CS', ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?
        )
        ON CONFLICT (user_id) DO NOTHING
        """,
        userId,
        "{\"pythonSelfRatedLevel\": 3}",
        "{\"primaryGoal\": \"DEMO\"}",
        "{}",
        "{\"mediaPriority\": [\"HANDOUT\"]}",
        "{}",
        "{}",
        "{}",
        "测试画像");
  }

  private void seedActivity(Long userId) {
    String kcCode = "PY43-KC-001";
    jdbcTemplate.update(
        "INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence) VALUES (?, ?, 0.42, 0.5)",
        userId,
        kcCode);
    jdbcTemplate.update(
        """
        INSERT INTO kc_mastery_event (user_id, kc_code, source, delta, reason, created_at)
        VALUES (?, ?, 'PRACTICE_PASS', 0.1, 'test', now() - interval '1 day')
        """,
        userId,
        kcCode);
    Long submissionId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO practice_submission (user_id, problem_id, source, language, code, status, submitted_at)
            VALUES (?, 476, 'TRAINING', 'PYTHON_3_11', ?, 'PASSED', now() - interval '2 hour')
            RETURNING id
            """,
            Long.class,
            userId,
            "print(\"hi\")");
    jdbcTemplate.update(
        """
        INSERT INTO judge_result (submission_id, verdict, passed_cases, total_cases, failure_reason)
        VALUES (?, 'AC', 3, 3, NULL)
        """,
        submissionId);
    jdbcTemplate.update(
        """
        INSERT INTO bug_mine_session (user_id, submission_id, problem_id, status, opened_at, closed_at)
        VALUES (?, ?, 476, 'RESOLVED', now() - interval '1 day', now())
        """,
        userId,
        submissionId);
    jdbcTemplate.update(
        """
        INSERT INTO learning_resource (
          user_id, pack_id, kc_codes, type, title, content_format, content,
          summary, feedback_status, created_at
        ) VALUES (?, 43, ARRAY[?]::TEXT[], 'HANDOUT', 'demo', 'MARKDOWN', '{}'::jsonb, 'demo', 'LIKED', now() - interval '3 hour')
        """,
        userId,
        kcCode);
    jdbcTemplate.update(
        """
        INSERT INTO profile_revision (user_id, source, diff, created_at)
        VALUES (?, 'PRACTICE_PASS', '{"kind":"practice_pass"}'::jsonb, now() - interval '1 day')
        """,
        userId);
  }

  private String narrativePayload() throws Exception {
    Map<String, Object> body =
        Map.of(
            "narrative_md", "# 本周复盘\\n- KC 总掌握度：0.42score\\n- PY43-KC-001 变量",
            "key_recommendations", List.of("继续保持"),
            "path_diff_explainer", "新路径会调整节奏",
            "safety_result", Map.of("passed", true, "reasons", List.of()),
            "grounding_result",
                Map.of(
                    "passed", true,
                    "reasons", List.of(),
                    "unknown_numbers", List.of(),
                    "unknown_kc", List.of()),
            "deterministic_signature", UUID.randomUUID().toString(),
            "status", "SUCCESS");
    return objectMapper.writeValueAsString(body);
  }

  private String pathPlannerPayload() throws Exception {
    java.util.LinkedHashMap<String, Object> node = new java.util.LinkedHashMap<>();
    node.put("day_index", 1);
    node.put("order_in_day", 1);
    node.put("kc_code", "PY43-KC-001");
    node.put("node_type", "READ_RESOURCE");
    node.put("reason_summary", "复习");
    node.put("evidence_summary", "evidence");
    node.put("suggested_resource_type", "HANDOUT");
    node.put("suggested_problem_id", null);
    node.put("source_classroom_quest_id", null);

    java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("user_id", 0);
    body.put("pack_id", 43);
    body.put("goal_summary", "draft demo");
    body.put("nodes", List.of(node));
    body.put("safety_result", Map.of("passed", true, "reasons", List.of()));
    body.put(
        "grounding_result",
        Map.of("passed", true, "reasons", List.of(), "unknown_kc", List.of()));
    body.put("deterministic_signature", UUID.randomUUID().toString());
    body.put("status", "SUCCESS");
    return objectMapper.writeValueAsString(body);
  }

  private Long userId(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username = ?", Long.class, username);
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
