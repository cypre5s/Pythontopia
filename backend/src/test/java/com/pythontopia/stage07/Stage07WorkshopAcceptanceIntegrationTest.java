package com.pythontopia.stage07;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.PythontopiaApplication;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
class Stage07WorkshopAcceptanceIntegrationTest {

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
    registry.add("pythontopia.workshop.maxResourcesPerHour", () -> "3");
    registry.add("pythontopia.workshop.masteryDailyCapPerKc", () -> "0.10");
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper objectMapper;
  @Autowired @Qualifier("workshopRestTemplate") RestTemplate workshopRestTemplate;

  @Test
  void studentRequestsHandoutAndResourceIsPersistedWithEvidence() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(workshopRestTemplate).build();
    String token = registerAndLogin("workshop-stu01");
    Long userId = userId("workshop-stu01");
    seedProfile(userId);

    graphServer
        .expect(
            MockRestRequestMatchers.requestTo("http://graph.test/agents/resource/produce"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                successHandoutResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/workshop/resources",
            HttpMethod.POST,
            bearer(
                token,
                Map.of(
                    "kcCodes",
                    List.of("PY43-KC-001"),
                    "type",
                    "HANDOUT")),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("status")).isEqualTo("SUCCESS");
    Long resourceId = ((Number) response.getBody().get("resourceId")).longValue();
    Long runId = ((Number) response.getBody().get("runId")).longValue();
    assertThat(resourceId).isPositive();
    assertThat(runId).isPositive();

    Integer rowCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM learning_resource WHERE user_id = ? AND type = 'HANDOUT'",
            Integer.class,
            userId);
    assertThat(rowCount).isEqualTo(1);

    Integer runRow =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_run WHERE user_id = ? AND scenario = 'RESOURCE_HANDOUT' AND status = 'SUCCESS'",
            Integer.class,
            userId);
    assertThat(runRow).isEqualTo(1);

    Integer events =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND kc_code = 'PY43-KC-001' AND source = 'RESOURCE_READ'",
            Integer.class,
            userId);
    assertThat(events).isZero();

    ResponseEntity<Map> consumption =
        restTemplate.exchange(
            "/api/workshop/resources/" + resourceId + "/consumptions",
            HttpMethod.POST,
            bearer(token, Map.of()),
            Map.class);
    assertThat(consumption.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(consumption.getBody().get("nextAction")).isEqualTo("micro_check");

    events =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND kc_code = 'PY43-KC-001' AND source = 'RESOURCE_READ'",
            Integer.class,
            userId);
    assertThat(events).isZero();

    ResponseEntity<Map> passedCheck =
        restTemplate.exchange(
            "/api/workshop/resources/" + resourceId + "/checks",
            HttpMethod.POST,
            bearer(
                token,
                Map.of(
                    "kcCode",
                    "PY43-KC-001",
                    "evidenceType",
                    "MICRO_QUIZ",
                    "answerText",
                    "input 函数用于读取用户在终端输入的内容")),
            Map.class);
    assertThat(passedCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(passedCheck.getBody().get("status")).isEqualTo("PASSED");
    assertThat(passedCheck.getBody().get("nextAction")).isEqualTo("pass");
    assertThat(passedCheck.getBody().get("loopState")).isEqualTo("PASSED");

    Integer passedEvents =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND kc_code = 'PY43-KC-001' AND source = 'MICRO_CHECK_PASS'",
            Integer.class,
            userId);
    assertThat(passedEvents).isEqualTo(1);

    Integer passedChecks =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_learning_check WHERE user_id = ? AND resource_id = ? AND status = 'PASSED'",
            Integer.class,
            userId,
            resourceId);
    assertThat(passedChecks).isEqualTo(1);

    ResponseEntity<Map> failedCheck =
        restTemplate.exchange(
            "/api/workshop/resources/" + resourceId + "/checks",
            HttpMethod.POST,
            bearer(
                token,
                Map.of(
                    "kcCode",
                    "PY43-KC-001",
                    "evidenceType",
                    "MICRO_QUIZ",
                    "answerText",
                    "我看完了，还行")),
            Map.class);
    assertThat(failedCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(failedCheck.getBody().get("status")).isEqualTo("FAILED");
    assertThat(failedCheck.getBody().get("nextAction")).isEqualTo("remediate");
    assertThat(failedCheck.getBody().get("loopState")).isEqualTo("NEED_REMEDIATION");
    assertThat(failedCheck.getBody().get("remediationType")).isEqualTo("PRACTICE");

    Integer failedEvents =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND kc_code = 'PY43-KC-001' AND source = 'MICRO_CHECK_FAIL'",
            Integer.class,
            userId);
    assertThat(failedEvents).isEqualTo(1);

    ResponseEntity<Map> effectSummary =
        restTemplate.exchange(
            "/api/learner/effect/summary",
            HttpMethod.GET,
            bearer(token, null),
            Map.class);
    assertThat(effectSummary.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(effectSummary.getBody().get("resourceCount")).isEqualTo(1);
    assertThat(effectSummary.getBody().get("consumedResourceCount")).isEqualTo(1);
    assertThat(effectSummary.getBody().get("checkCount")).isEqualTo(2);
    assertThat(effectSummary.getBody().get("passedCheckCount")).isEqualTo(1);
    assertThat(effectSummary.getBody().get("failedCheckCount")).isEqualTo(1);
    assertThat(((Number) effectSummary.getBody().get("checkPassRate")).doubleValue())
        .isEqualTo(0.5);
    assertThat(((Number) effectSummary.getBody().get("totalMasteryDelta")).doubleValue())
        .isEqualTo(0.03);
    assertThat(effectSummary.getBody().get("positiveResourceReadEvents")).isEqualTo(0);

    ResponseEntity<Map> tutorBench =
        restTemplate.exchange(
            "/api/tutorbench/runs",
            HttpMethod.POST,
            bearer(token, Map.of()),
            Map.class);
    assertThat(tutorBench.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(tutorBench.getBody().get("totalCases")).isEqualTo(6);
    assertThat(tutorBench.getBody().get("passedCases")).isEqualTo(6);
    assertThat(((Number) tutorBench.getBody().get("passRate")).doubleValue()).isEqualTo(1.0);

    ResponseEntity<Map> protocol =
        restTemplate.exchange(
            "/api/protocol/descriptor",
            HttpMethod.GET,
            bearer(token, null),
            Map.class);
    assertThat(protocol.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(protocol.getBody().toString()).contains("workshop.resource.check.submit");
    assertThat(protocol.getBody().toString()).contains("LearningEffectAgent");
    assertThat(protocol.getBody().toString()).contains("RUN_FINISHED");

    ResponseEntity<List> list =
        restTemplate.exchange(
            "/api/workshop/resources?type=HANDOUT",
            HttpMethod.GET,
            bearer(token, null),
            List.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody()).hasSize(1);

    ResponseEntity<Map> single =
        restTemplate.exchange(
            "/api/workshop/resources/" + resourceId,
            HttpMethod.GET,
            bearer(token, null),
            Map.class);
    assertThat(single.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(single.getBody().get("title")).isEqualTo("Handout");
    @SuppressWarnings("unchecked")
    List<Number> evidence = (List<Number>) single.getBody().get("evidencePageIds");
    assertThat(evidence).contains(101);

    ResponseEntity<Void> feedback =
        restTemplate.exchange(
            "/api/workshop/resources/" + resourceId + "/feedback",
            HttpMethod.POST,
            bearer(token, Map.of("status", "LIKED", "note", "好评")),
            Void.class);
    assertThat(feedback.getStatusCode().is2xxSuccessful()).isTrue();

    String fb =
        jdbcTemplate.queryForObject(
            "SELECT feedback_status FROM learning_resource WHERE id = ?",
            String.class,
            resourceId);
    assertThat(fb).isEqualTo("LIKED");

    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void throttlingReturnsTooManyRequestsAfterCap() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(workshopRestTemplate).build();
    String token = registerAndLogin("workshop-stu02");
    Long userId = userId("workshop-stu02");
    seedProfile(userId);

    graphServer
        .expect(
            ExpectedCount.times(3),
            MockRestRequestMatchers.requestTo("http://graph.test/agents/resource/produce"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                successHandoutResponse(), MediaType.APPLICATION_JSON));

    for (int i = 0; i < 3; i++) {
      ResponseEntity<Map> ok =
          restTemplate.exchange(
              "/api/workshop/resources",
              HttpMethod.POST,
              bearer(
                  token,
                  Map.of(
                      "kcCodes",
                      List.of("PY43-KC-001"),
                      "type",
                      "HANDOUT")),
              Map.class);
      assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    ResponseEntity<Map> throttled =
        restTemplate.exchange(
            "/api/workshop/resources",
            HttpMethod.POST,
            bearer(
                token,
                Map.of(
                    "kcCodes",
                    List.of("PY43-KC-001"),
                    "type",
                    "HANDOUT")),
            Map.class);
    assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(throttled.getBody().get("code")).isEqualTo("RESOURCE_THROTTLED");
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void concurrentMicroCheckRetryReplaysOnePersistedResultAndRejectsChangedPayload() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(workshopRestTemplate).build();
    String token = registerAndLogin("workshop-check-idempotency");
    Long userId = userId("workshop-check-idempotency");
    seedProfile(userId);
    graphServer
        .expect(
            MockRestRequestMatchers.requestTo("http://graph.test/agents/resource/produce"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                successHandoutResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> createdResource =
        restTemplate.exchange(
            "/api/workshop/resources",
            HttpMethod.POST,
            bearer(token, Map.of("kcCodes", List.of("PY43-KC-001"), "type", "HANDOUT")),
            Map.class);
    assertThat(createdResource.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long resourceId = ((Number) createdResource.getBody().get("resourceId")).longValue();
    Map<String, Object> checkBody =
        Map.of(
            "kcCode",
            "PY43-KC-001",
            "evidenceType",
            "MICRO_QUIZ",
            "answerText",
            "input 函数用于读取用户在终端输入的内容");
    HttpEntity<?> retryRequest =
        bearerWithIdempotencyKey(token, checkBody, "micro-check-retry-1");
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Future<ResponseEntity<Map>> first =
        executor.submit(
            () -> {
              ready.countDown();
              start.await();
              return restTemplate.exchange(
                  "/api/workshop/resources/" + resourceId + "/checks",
                  HttpMethod.POST,
                  retryRequest,
                  Map.class);
            });
    Future<ResponseEntity<Map>> second =
        executor.submit(
            () -> {
              ready.countDown();
              start.await();
              return restTemplate.exchange(
                  "/api/workshop/resources/" + resourceId + "/checks",
                  HttpMethod.POST,
                  retryRequest,
                  Map.class);
            });
    try {
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      ResponseEntity<Map> firstResponse = first.get(30, TimeUnit.SECONDS);
      ResponseEntity<Map> replayResponse = second.get(30, TimeUnit.SECONDS);

      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(replayResponse.getBody()).isEqualTo(firstResponse.getBody());
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM resource_learning_check WHERE user_id = ? AND resource_id = ?",
                  Integer.class,
                  userId,
                  resourceId))
          .isEqualTo(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND kc_code = 'PY43-KC-001' AND source = 'MICRO_CHECK_PASS'",
                  Integer.class,
                  userId))
          .isEqualTo(1);

      ResponseEntity<Map> changedPayload =
          restTemplate.exchange(
              "/api/workshop/resources/" + resourceId + "/checks",
              HttpMethod.POST,
              bearerWithIdempotencyKey(
                  token,
                  Map.of(
                      "kcCode",
                      "PY43-KC-001",
                      "evidenceType",
                      "MICRO_QUIZ",
                      "answerText",
                      "changed answer"),
                  "micro-check-retry-1"),
              Map.class);
      assertThat(changedPayload.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(changedPayload.getBody().get("code"))
          .isEqualTo("RESOURCE_CHECK_IDEMPOTENCY_CONFLICT");
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM resource_learning_check WHERE user_id = ? AND resource_id = ?",
                  Integer.class,
                  userId,
                  resourceId))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void unknownKcCodeReturns400() {
    String token = registerAndLogin("workshop-stu03");
    seedProfile(userId("workshop-stu03"));

    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/workshop/resources",
            HttpMethod.POST,
            bearer(
                token,
                Map.of(
                    "kcCodes",
                    List.of("PY43-KC-999"),
                    "type",
                    "HANDOUT")),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("code")).isEqualTo("RESOURCE_KC_UNKNOWN");
  }

  @Test
  void safetyFailureMarksRunAsFailedAndDoesNotPersistResource() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(workshopRestTemplate).build();
    String token = registerAndLogin("workshop-stu04");
    Long userId = userId("workshop-stu04");
    seedProfile(userId);

    graphServer
        .expect(
            MockRestRequestMatchers.requestTo("http://graph.test/agents/resource/produce"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                failedResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/workshop/resources",
            HttpMethod.POST,
            bearer(
                token,
                Map.of(
                    "kcCodes",
                    List.of("PY43-KC-001"),
                    "type",
                    "HANDOUT")),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().get("code")).isEqualTo("RESOURCE_GENERATION_FAILED");

    Integer rows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM learning_resource WHERE user_id = ?",
            Integer.class,
            userId);
    assertThat(rows).isZero();
    Integer failedRuns =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_run WHERE user_id = ? AND status = 'FAILED'",
            Integer.class,
            userId);
    assertThat(failedRuns).isEqualTo(1);
    graphServer.verify(Duration.ofSeconds(2));
  }

  private String successHandoutResponse() throws Exception {
    Map<String, Object> body =
        Map.of(
            "artifact",
                Map.of(
                    "title", "Handout",
                    "summary", "input 函数用于读取用户输入",
                    "content_format", "MARKDOWN",
                    "handout",
                        Map.of(
                            "sections",
                            List.of(
                                Map.of(
                                    "heading", "测试讲义",
                                    "paragraphs", List.of("input 函数用于读取用户输入"),
                                    "code_snippets", List.of(),
                                    "page_refs",
                                    List.of(
                                        Map.of(
                                            "page_id", 101,
                                            "page_no", 12,
                                            "chapter_id", 3)))))),
            "type", "HANDOUT",
            "evidence_page_ids", List.of(101),
            "grounding_result",
                Map.of("passed", true, "reasons", List.of(), "no_evidence_found", false, "retried", false),
            "safety_result", Map.of("passed", true, "reasons", List.of()),
            "graph_trace",
                List.of(Map.of("node", "evidence_fetcher", "elapsed_ms", 1, "status", "ok")),
            "status", "SUCCESS");
    return objectMapper.writeValueAsString(body);
  }

  private String failedResponse() throws Exception {
    Map<String, Object> body =
        Map.of(
            "artifact",
                Map.of(
                    "title", "Handout",
                    "summary", "summary",
                    "content_format", "MARKDOWN",
                    "handout", Map.of("sections", List.of(Map.of("heading", "x", "paragraphs", List.of("y"))))),
            "type", "HANDOUT",
            "evidence_page_ids", List.of(),
            "grounding_result",
                Map.of("passed", false, "reasons", List.of("SAFETY_FAILED"), "no_evidence_found", false, "retried", false),
            "safety_result", Map.of("passed", false, "reasons", List.of("ANSWER_LEAK:answer_key")),
            "graph_trace", List.of(),
            "status", "FAILED");
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

  private HttpEntity<?> bearerWithIdempotencyKey(String token, Object body, String key) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.set("Idempotency-Key", key);
    return new HttpEntity<>(body, headers);
  }
}
