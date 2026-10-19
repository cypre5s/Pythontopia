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
            1,
            "PY43-KC-001",
            "z title",
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
    BossInternalProblem alphaProblem = withStatement(problem, "source-auth-alpha", "a title");
    insertReview(alphaProblem, teacherId);
    Long reviewId =
        insertReview(problem, teacherId);

    ResponseEntity<Map> sortedPage =
        restTemplate.exchange(
            "/api/teacher/problem-reviews/page?page=0&size=20&sortBy=STATEMENT&sortDirection=ASC",
            HttpMethod.GET,
            bearer(adminToken, null),
            Map.class);
    assertThat(sortedPage.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map<String, Object>> sortedContent =
        (List<Map<String, Object>>) sortedPage.getBody().get("content");
    assertThat(sortedContent)
        .extracting(row -> ((Map<String, Object>) row.get("problem")).get("statement"))
        .containsExactly("a title", "z title");

    ResponseEntity<Map> reverseSortedPage =
        restTemplate.exchange(
            "/api/teacher/problem-reviews/page?page=0&size=20&sortBy=STATEMENT&sortDirection=DESC",
            HttpMethod.GET,
            bearer(adminToken, null),
            Map.class);
    List<Map<String, Object>> reverseSortedContent =
        (List<Map<String, Object>>) reverseSortedPage.getBody().get("content");
    assertThat(reverseSortedContent)
        .extracting(row -> ((Map<String, Object>) row.get("problem")).get("statement"))
        .containsExactly("z title", "a title");

    ResponseEntity<Map> invalidSort =
        restTemplate.exchange(
            "/api/teacher/problem-reviews/page?page=0&size=20&sortBy=PAYLOAD&sortDirection=DESC",
            HttpMethod.GET,
            bearer(adminToken, null),
            Map.class);
    assertThat(invalidSort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<Map> invalidDirection =
        restTemplate.exchange(
            "/api/teacher/problem-reviews/page?page=0&size=20&sortBy=STATEMENT&sortDirection=SIDEWAYS",
            HttpMethod.GET,
            bearer(adminToken, null),
            Map.class);
    assertThat(invalidDirection.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

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
        .containsEntry("difficulty", 1)
        .doesNotContainKeys("testCases", "referenceSolution", "payload");
    assertThat((String) studentBoss.getBody().get("statement")).contains("z title");
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

  private Long insertReview(BossInternalProblem problem, long requesterId) throws Exception {
    return jdbcTemplate.queryForObject(
        "INSERT INTO boss_problem_review"
            + " (source_problem_id, chapter, stage, kc_code, kind, difficulty, payload,"
            + " requester_id)"
            + " VALUES (?, 1, 1, 'PY43-KC-001', 'debug', ?, ?::jsonb, ?) RETURNING id",
        Long.class,
        problem.problemId(),
        problem.difficulty(),
        objectMapper.writeValueAsString(problem),
        requesterId);
  }

  private BossInternalProblem withStatement(
      BossInternalProblem problem, String problemId, String statement) {
    return new BossInternalProblem(
        problemId,
        problem.chapter(),
        problem.stage(),
        problem.difficulty(),
        problem.kcCode(),
        statement,
        problem.hints(),
        problem.sampleInput(),
        problem.sampleOutput(),
        problem.totalTestCases(),
        problem.reviewed(),
        problem.aiGenerated(),
        problem.testCases(),
        problem.referenceSolution());
  }
}
