package com.pythontopia.stage09;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Stage09SubmissionAcceptanceIntegrationTest {

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

  @Test
  void publicProblemEndpointHidesSensitiveFields() {
    String token = registerAndLogin("submit-stu01");
    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/problems/476/public",
            HttpMethod.GET,
            bearer(token, null),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> body = response.getBody();
    assertThat(body).doesNotContainKey("hidden_test_cases");
    assertThat(body).doesNotContainKey("hiddenTestCases");
    assertThat(body).doesNotContainKey("expected_output_pattern");
    assertThat(body).doesNotContainKey("reference_solution_code");
    assertThat(body).containsKey("samples");
  }

  @Test
  void acceptedSubmissionPassesAllSamplesAndRecordsMasteryEvent() {
    String token = registerAndLogin("submit-stu02");
    Long userId = userId("submit-stu02");

    String code =
        "radius = float(input())\n"
            + "area = 3.1415 * radius * radius\n"
            + "print(f\"{area:.4f}\")\n";
    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/submissions",
            HttpMethod.POST,
            bearer(token, Map.of("problemId", 476, "code", code, "source", "TRAINING")),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("status")).isEqualTo("PASSED");
    Long submissionId = ((Number) response.getBody().get("id")).longValue();

    Integer passed =
        jdbcTemplate.queryForObject(
            "SELECT passed_cases FROM judge_result WHERE submission_id = ?",
            Integer.class,
            submissionId);
    Integer total =
        jdbcTemplate.queryForObject(
            "SELECT total_cases FROM judge_result WHERE submission_id = ?",
            Integer.class,
            submissionId);
    assertThat(passed).isEqualTo(total).isPositive();

    String verdict =
        jdbcTemplate.queryForObject(
            "SELECT verdict FROM judge_result WHERE submission_id = ?",
            String.class,
            submissionId);
    assertThat(verdict).isEqualTo("AC");

    Integer mastery =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND source = 'PRACTICE_PASS'",
            Integer.class,
            userId);
    assertThat(mastery).isGreaterThanOrEqualTo(1);
  }

  @Test
  void bannedImportReturnsCe() {
    String token = registerAndLogin("submit-stu03");
    String code = "import os\nprint(os.getcwd())";
    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/submissions",
            HttpMethod.POST,
            bearer(token, Map.of("problemId", 476, "code", code, "source", "TRAINING")),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("status")).isEqualTo("FAILED");
    Long submissionId = ((Number) response.getBody().get("id")).longValue();
    String verdict =
        jdbcTemplate.queryForObject(
            "SELECT verdict FROM judge_result WHERE submission_id = ?",
            String.class,
            submissionId);
    assertThat(verdict).isEqualTo("CE");
  }

  @Test
  void wrongOutputReturnsWa() {
    String token = registerAndLogin("submit-stu04");
    Long userId = userId("submit-stu04");
    String code = "print(\"42\")";
    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/submissions",
            HttpMethod.POST,
            bearer(token, Map.of("problemId", 476, "code", code, "source", "TRAINING")),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long submissionId = ((Number) response.getBody().get("id")).longValue();
    String verdict =
        jdbcTemplate.queryForObject(
            "SELECT verdict FROM judge_result WHERE submission_id = ?",
            String.class,
            submissionId);
    assertThat(verdict).isEqualTo("WA");

    Integer events =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND source = 'PRACTICE_FAIL'",
            Integer.class,
            userId);
    assertThat(events).isGreaterThanOrEqualTo(1);
  }

  @Test
  void secondSubmissionWithin10SecondsReturns429() {
    String token = registerAndLogin("submit-stu05");
    String code = "print(\"hi\")";
    Map<String, Object> body = Map.of("problemId", 476, "code", code, "source", "TRAINING");
    ResponseEntity<Map> first =
        restTemplate.exchange(
            "/api/submissions", HttpMethod.POST, bearer(token, body), Map.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    ResponseEntity<Map> second =
        restTemplate.exchange(
            "/api/submissions", HttpMethod.POST, bearer(token, body), Map.class);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(second.getBody().get("code")).isEqualTo("SUBMISSION_THROTTLED");
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
