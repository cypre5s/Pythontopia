package com.pythontopia.stage10;

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
class Stage10BugMineAcceptanceIntegrationTest {

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
    registry.add("pythontopia.bugMine.maxRetriesPerSession", () -> "2");
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper objectMapper;
  @Autowired RestTemplate graphRestTemplate;

  @Test
  void openSessionPersistsAllCardsAndHidesHints() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("mine-stu01");
    Long userId = userId("mine-stu01");
    Long submissionId = seedFailedSubmission(userId, 476L);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(token, Map.of("submissionId", submissionId)),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long sessionId = ((Number) response.getBody().get("id")).longValue();

    Integer cards =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bug_mine_card WHERE session_id = ?",
            Integer.class,
            sessionId);
    assertThat(cards).isEqualTo(6);

    Integer revealed =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bug_mine_card WHERE session_id = ? AND revealed = TRUE",
            Integer.class,
            sessionId);
    assertThat(revealed).isEqualTo(1); // only layer 1 is revealed by default

    ResponseEntity<Map> fetched =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId,
            HttpMethod.GET,
            bearer(token, null),
            Map.class);
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> cardList = (List<Map<String, Object>>) fetched.getBody().get("cards");
    assertThat(cardList).hasSize(6);

    Long layer2CardId =
        cardList.stream()
            .filter(card -> "HINT_LAYER_2".equals(card.get("cardType")))
            .map(card -> ((Number) card.get("id")).longValue())
            .findFirst()
            .orElseThrow();
    ResponseEntity<Map> reveal =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId + "/cards/" + layer2CardId + "/reveal",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(reveal.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Boolean) reveal.getBody().get("revealed")).isTrue();

    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void retrySubmitResolvedSessionAndIncrementsMastery() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("mine-stu02");
    Long userId = userId("mine-stu02");
    Long submissionId = seedFailedSubmission(userId, 476L);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> open =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(token, Map.of("submissionId", submissionId)),
            Map.class);
    Long sessionId = ((Number) open.getBody().get("id")).longValue();

    String code =
        "radius = float(input())\n"
            + "area = 3.1415 * radius * radius\n"
            + "print(f\"{area:.4f}\")\n";
    ResponseEntity<Map> retry =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId + "/retry-submit",
            HttpMethod.POST,
            bearer(token, Map.of("code", code)),
            Map.class);
    assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(retry.getBody().get("status")).isEqualTo("PASSED");
    assertThat(retry.getBody().get("source")).isEqualTo("BUG_MINE_RETRY");

    String status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM bug_mine_session WHERE id = ?",
            String.class,
            sessionId);
    assertThat(status).isEqualTo("RESOLVED");

    Integer debugFix =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND source = 'DEBUG_FIX'",
            Integer.class,
            userId);
    assertThat(debugFix).isGreaterThanOrEqualTo(1);
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void crossUserSessionAccessReturns404() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String stuA = registerAndLogin("mine-stu03");
    Long aId = userId("mine-stu03");
    Long submissionId = seedFailedSubmission(aId, 476L);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));
    ResponseEntity<Map> open =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(stuA, Map.of("submissionId", submissionId)),
            Map.class);
    Long sessionId = ((Number) open.getBody().get("id")).longValue();

    String stuB = registerAndLogin("mine-stu04");
    ResponseEntity<Map> blocked =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId,
            HttpMethod.GET,
            bearer(stuB, null),
            Map.class);
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void retryExceedsLimitReturns429() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("mine-stu05");
    Long userId = userId("mine-stu05");
    Long submissionId = seedFailedSubmission(userId, 476L);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));
    ResponseEntity<Map> open =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(token, Map.of("submissionId", submissionId)),
            Map.class);
    Long sessionId = ((Number) open.getBody().get("id")).longValue();

    // Pre-set retry count to the maximum to deterministically hit the limit
    jdbcTemplate.update("UPDATE bug_mine_session SET retry_count = 2 WHERE id = ?", sessionId);
    ResponseEntity<Map> third =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId + "/retry-submit",
            HttpMethod.POST,
            bearer(token, Map.of("code", "print(\"hi\")")),
            Map.class);
    assertThat(third.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(third.getBody().get("code")).isEqualTo("BUG_MINE_RETRY_EXCEEDED");
    graphServer.verify(Duration.ofSeconds(2));
  }

  private String graphDebugResponse() throws Exception {
    Map<String, Object> body =
        Map.of(
            "counter_example",
                Map.of(
                    "stdin", "25",
                    "expected_stdout", "1963.4375",
                    "your_stdout", "25",
                    "diff_summary", "shape diff",
                    "redacted", false),
            "variable_trace_summary", Map.of("steps", List.of(), "truncated", false),
            "wrong_assumption",
                Map.of(
                    "you_thought", "你以为 print 直接给结果",
                    "actually", "实际需要先计算面积",
                    "evidence_kc_codes", List.of("PY43-KC-016")),
            "hint_layers",
                List.of(
                    Map.of(
                        "layer", 1,
                        "headline", "想一想",
                        "body", "请先用文字描述你想做什么",
                        "related_pages", List.of(12)),
                    Map.of(
                        "layer", 2,
                        "headline", "看代码",
                        "body", "关注第 2 行的算式",
                        "related_pages", List.of(13)),
                    Map.of(
                        "layer", 3,
                        "headline", "改造方向",
                        "body", "请先读取输入，再相乘计算面积",
                        "related_pages", List.of(14))),
            "retry_advice",
                Map.of("summary", "再读题面", "expected_kc_codes", List.of("PY43-KC-016")),
            "safety_passed", true,
            "safety_reasons", List.of());
    return objectMapper.writeValueAsString(body);
  }

  private Long seedFailedSubmission(Long userId, Long problemId) {
    Long submissionId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO practice_submission (user_id, problem_id, source, language, code, status, submitted_at)
            VALUES (?, ?, 'TRAINING', 'PYTHON_3_11', ?, 'FAILED', now() - interval '2 minutes')
            RETURNING id
            """,
            Long.class,
            userId,
            problemId,
            "print(\"42\")");
    jdbcTemplate.update(
        """
        INSERT INTO judge_result (submission_id, verdict, passed_cases, total_cases, failure_reason)
        VALUES (?, 'WA', 0, 3, '与样例不一致')
        """,
        submissionId);
    return submissionId;
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
