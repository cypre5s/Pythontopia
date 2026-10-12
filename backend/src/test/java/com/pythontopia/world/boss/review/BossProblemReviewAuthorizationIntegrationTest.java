package com.pythontopia.world.boss.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.PythontopiaApplication;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 真 JWT + 真 PostgreSQL：学生不能读取/决策，教师 DTO 永不泄露 hidden tests。 */
@Testcontainers
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BossProblemReviewAuthorizationIntegrationTest {

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
        () -> "problem-review-test-secret-must-be-long-enough");
    registry.add(
        "pythontopia.security.teacherInviteCode",
        () -> "problem-review-private-teacher-code");
    registry.add("pythontopia.services.graphUrl", () -> "http://graph.test");
    registry.add("pythontopia.judge.python", () -> "python3");
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper objectMapper;

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void realJwtReviewDecisionPublishesSafeApprovedProblemToStudentBoss() throws Exception {
    restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    String teacherToken = registerAndLogin("problem-review-auth-teacher", "TEACHER", true);
    String studentToken = registerAndLogin("problem-review-auth-student", "STUDENT", false);
    registerAndLogin("problem-review-auth-admin", "TEACHER", true);
    jdbcTemplate.update(
        "UPDATE users SET role = 'ADMIN' WHERE username = 'problem-review-auth-admin'");
    String adminToken = login("problem-review-auth-admin");
    Long teacherId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = 'problem-review-auth-teacher'", Long.class);
    Long studentId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = 'problem-review-auth-student'", Long.class);
    BossInternalProblem problem =
        new BossInternalProblem(
            "source-auth-1",
            1,
            1,
            2,
            "PY43-KC-001",
            "输入两个整数并输出和",
            List.of("读取输入"),
            "2 3",
            "5",
            2,
            true,
            true,
            List.of(
                new BossTestCasePayload("2 3", "5", true),
                new BossTestCasePayload("-1 8", "7", false)),
            "a, b = map(int, input().split())\nprint(a + b)");
    Long reviewId =
        jdbcTemplate.queryForObject(
            "INSERT INTO boss_problem_review"
                + " (source_problem_id, chapter, stage, kc_code, kind, difficulty, payload,"
                + " requester_id)"
                + " VALUES (?, 1, 1, 'PY43-KC-001', 'debug', 2, ?::jsonb, ?) RETURNING id",
            Long.class,
            problem.problemId(),
            objectMapper.writeValueAsString(problem),
            teacherId);

    ResponseEntity<Map> studentList =
        restTemplate.exchange(
            "/api/teacher/problem-reviews",
            HttpMethod.GET,
            bearer(studentToken, null),
            Map.class);
    assertThat(studentList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<Map> studentDecision =
        restTemplate.exchange(
            "/api/teacher/problem-reviews/" + reviewId,
            HttpMethod.PATCH,
            bearer(
                studentToken,
                Map.of("decision", "APPROVED", "reason", "forbidden", "version", 1)),
            Map.class);
    assertThat(studentDecision.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<Map> studentCreate =
        restTemplate.exchange(
            "/api/teacher/problem-reviews",
            HttpMethod.POST,
            bearer(studentToken, Map.of("chapter", 1, "stage", 1, "difficulty", 2)),
            Map.class);
    assertThat(studentCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<List> teacherList =
        restTemplate.exchange(
            "/api/teacher/problem-reviews",
            HttpMethod.GET,
            bearer(teacherToken, null),
            List.class);
    assertThat(teacherList.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> row = (Map<String, Object>) teacherList.getBody().get(0);
    Map<String, Object> publicProblem = (Map<String, Object>) row.get("problem");
    assertThat(publicProblem).containsEntry("problemId", "source-auth-1");
    assertThat(publicProblem).doesNotContainKeys("testCases", "referenceSolution");
    assertThat(row).doesNotContainKeys("payload");

    ResponseEntity<List> adminList =
        restTemplate.exchange(
            "/api/teacher/problem-reviews",
            HttpMethod.GET,
            bearer(adminToken, null),
            List.class);
    assertThat(adminList.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> approved =
        restTemplate.exchange(
            "/api/teacher/problem-reviews/" + reviewId,
            HttpMethod.PATCH,
            bearer(
                teacherToken,
                Map.of(
                    "decision",
                    "APPROVED",
                    "reason",
                    "题面、样例与隐藏测试范围已复核",
                    "version",
                    1)),
            Map.class);
    assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(approved.getBody())
        .containsEntry("decision", "APPROVED")
        .containsEntry("version", 2)
        .doesNotContainKeys("payload", "testCases", "referenceSolution");
    assertThat((Map<String, Object>) approved.getBody().get("problem"))
        .doesNotContainKeys("testCases", "referenceSolution");

    ResponseEntity<Map> staleDecision =
        restTemplate.exchange(
            "/api/teacher/problem-reviews/" + reviewId,
            HttpMethod.PATCH,
            bearer(
                adminToken,
                Map.of("decision", "REJECTED", "reason", "stale", "version", 1)),
            Map.class);
    assertThat(staleDecision.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(staleDecision.getBody())
        .containsEntry("code", "PROBLEM_REVIEW_VERSION_CONFLICT");

    ResponseEntity<Map> studentBoss =
        restTemplate.exchange(
            "/api/world/boss/1/problem",
            HttpMethod.POST,
            bearer(
                studentToken,
                Map.of(
                    "stage",
                    1,
                    "difficulty",
                    2,
                    "preferredKc",
                    "PY43-KC-001",
                    "preferredKind",
                    "debug")),
            Map.class);
    assertThat(studentBoss.getStatusCode()).isEqualTo(HttpStatus.OK);
    String issuedProblemId = (String) studentBoss.getBody().get("problemId");
    assertThat(studentBoss.getBody())
        .doesNotContainKeys("testCases", "referenceSolution", "payload");
    assertThat(issuedProblemId).startsWith("issue-");
    String persistedPayload =
        jdbcTemplate.queryForObject(
            "SELECT payload::text FROM pregenerated_problem"
                + " WHERE user_id = ? AND problem_id = ? ORDER BY id DESC LIMIT 1",
            String.class,
            studentId,
            issuedProblemId);
    assertThat(persistedPayload).contains("testCases").contains("referenceSolution");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private String registerAndLogin(String username, String role, boolean teacher) {
    java.util.HashMap<String, Object> register = new java.util.HashMap<>();
    register.put("username", username);
    register.put("password", "P@ssw0rd1");
    register.put("displayName", username);
    register.put("role", role);
    if (teacher) {
      register.put("teacherInviteCode", "problem-review-private-teacher-code");
    }
    ResponseEntity<Map> registered =
        restTemplate.postForEntity("/api/auth/register", register, Map.class);
    assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return login(username);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private String login(String username) {
    ResponseEntity<Map> login =
        restTemplate.postForEntity(
            "/api/auth/login",
            Map.of("username", username, "password", "P@ssw0rd1"),
            Map.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    return (String) login.getBody().get("token");
  }

  private HttpEntity<?> bearer(String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(body, headers);
  }
}
