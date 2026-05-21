package com.pythontopia.stage04;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.PythontopiaApplication;
import java.time.Duration;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Stage04ProfileAcceptanceIntegrationTest {

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
    registry.add("pythontopia.security.jwt.secret", () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
    registry.add("pythontopia.services.graphUrl", () -> "http://graph.test");
  }

  @Autowired TestRestTemplate restTemplate;

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired ObjectMapper objectMapper;

  @Autowired RestTemplate graphRestTemplate;

  @Test
  void studentCompletesProfileInterviewAndCanReadProfileAndRevision() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("profile-stu01");

    ResponseEntity<Map> start =
        restTemplate.exchange(
            "/api/learner/profile/interview",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);
    String sessionId = (String) start.getBody().get("interviewSessionId");
    assertThat(UUID.fromString(sessionId)).isNotNull();

    ResponseEntity<Map> resumed =
        restTemplate.exchange(
            "/api/learner/profile/interview",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(resumed.getBody()).containsEntry("interviewSessionId", sessionId);

    graphServer.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/agents/profile/turn"))
        .andRespond(
            withJson(
                    """
                    {
                      "next_question": null,
                      "done": true,
                      "dimensions_filled": ["major","base_level","learning_goal","cognitive_style","learning_preference","error_patterns","practice_capability","emotion_persistence"],
                      "profile_draft": {
                        "major": "CS",
                        "base_level": {"programming_years": 1, "languages_known": ["C"], "python_self_rated_level": 2},
                        "learning_goal": {"kind": "EXAM", "deadline_iso": null, "target_kc_codes": ["PY43-KC-001"]},
                        "cognitive_style": {"field_dependent": 0.4, "sequential_vs_holistic": 0.5},
                        "learning_preference": {"media_priority": ["code", "text"], "session_minutes_per_day": 45},
                        "error_patterns": {"frequent_kc_codes": ["PY43-KC-001"], "common_categories": ["syntax"]},
                        "practice_capability": {"avg_attempts_per_problem": 2.0, "success_rate": 0.65},
                        "emotion_persistence": {"resilience": 0.7, "recent_mood": "neutral"},
                        "narrative_summary": "学生具备基础编程经验，目标是围绕课程 KC 稳步通过考试。"
                      },
                      "evidence_anchors": [{"source":"interview","ref":"turn","summary":"来自访谈"}],
                      "safety_result": {"passed": true, "reasons": []},
                      "grounding_result": {"passed": true, "reasons": []}
                    }
                    """));

    ResponseEntity<Map> turn =
        restTemplate.exchange(
            "/api/learner/profile/interview/" + sessionId + "/turn",
            HttpMethod.POST,
            bearer(token, Map.of("userMessage", "我是计算机大一，学过 C，目标是期末考试通过")),
            Map.class);
    assertThat(turn.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(turn.getBody()).containsEntry("done", true);

    ResponseEntity<Map> profile =
        restTemplate.exchange("/api/learner/profile", HttpMethod.GET, bearer(token, null), Map.class);
    assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(profile.getBody()).containsEntry("major", "CS");
    assertThat(((Map<?, ?>) profile.getBody().get("learningGoal")).get("kind").toString())
        .isEqualTo("EXAM");

    ResponseEntity<Map[]> revisions =
        restTemplate.exchange(
            "/api/learner/profile/revisions?limit=10",
            HttpMethod.GET,
            bearer(token, null),
            Map[].class);
    assertThat(revisions.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(revisions.getBody()).hasSizeGreaterThanOrEqualTo(1);
    assertThat(revisions.getBody()[0]).containsEntry("source", "INTERVIEW");

    Integer resourceRuns =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_run WHERE agent_name='ProfileAgent' AND status='SUCCESS'",
            Integer.class);
    assertThat(resourceRuns).isEqualTo(1);
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void groundingFailureRecordsPartialRunAndDoesNotWriteProfile() {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("profile-stu02");
    String sessionId =
        (String)
            restTemplate
                .exchange(
                    "/api/learner/profile/interview",
                    HttpMethod.POST,
                    bearer(token, null),
                    Map.class)
                .getBody()
                .get("interviewSessionId");

    graphServer.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/agents/profile/turn"))
        .andRespond(
            withJson(
                    """
                    {
                      "next_question": "请确认课程目录中的主题。",
                      "profile_draft": null,
                      "done": false,
                      "dimensions_filled": [],
                      "evidence_anchors": [],
                      "safety_result": {"passed": true, "reasons": []},
                      "grounding_result": {"passed": false, "reasons": ["Unknown KC code: PY43-KC-999"]}
                    }
                    """));

    ResponseEntity<Map> turn =
        restTemplate.exchange(
            "/api/learner/profile/interview/" + sessionId + "/turn",
            HttpMethod.POST,
            bearer(token, Map.of("userMessage", "我想学虚构知识点")),
            Map.class);

    assertThat(turn.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(turn.getBody()).containsEntry("done", false);
    ResponseEntity<Void> profile =
        restTemplate.exchange("/api/learner/profile", HttpMethod.GET, bearer(token, null), Void.class);
    assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    Integer partialRuns =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_run WHERE agent_name='ProfileAgent' AND status='PARTIAL'",
            Integer.class);
    assertThat(partialRuns).isEqualTo(1);
  }

  @Test
  void safetyFailureRecordsFailedRunAndDoesNotWriteProfile() {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("profile-stu03");
    String sessionId =
        (String)
            restTemplate
                .exchange(
                    "/api/learner/profile/interview",
                    HttpMethod.POST,
                    bearer(token, null),
                    Map.class)
                .getBody()
                .get("interviewSessionId");

    graphServer.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/agents/profile/turn"))
        .andRespond(
            withJson(
                    """
                    {
                      "next_question": null,
                      "profile_draft": null,
                      "done": true,
                      "dimensions_filled": [],
                      "evidence_anchors": [],
                      "safety_result": {"passed": false, "reasons": ["PII"]},
                      "grounding_result": {"passed": false, "reasons": ["SAFETY_FAILED"]}
                    }
                    """));

    ResponseEntity<Map> turn =
        restTemplate.exchange(
            "/api/learner/profile/interview/" + sessionId + "/turn",
            HttpMethod.POST,
            bearer(token, Map.of("userMessage", "敏感信息")),
            Map.class);

    assertThat(turn.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(((Map<?, ?>) turn.getBody().get("safetyResult")).get("passed")).isEqualTo(false);
    Integer failedRuns =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_run WHERE agent_name='ProfileAgent' AND status='FAILED'",
            Integer.class);
    assertThat(failedRuns).isEqualTo(1);
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
            "/api/auth/login", Map.of("username", username, "password", "P@ssw0rd1"), Map.class);
    return (String) response.getBody().get("token");
  }

  private HttpEntity<?> bearer(String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(body, headers);
  }

  private org.springframework.test.web.client.ResponseCreator withJson(String json) {
    return org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
        json, org.springframework.http.MediaType.APPLICATION_JSON);
  }
}
