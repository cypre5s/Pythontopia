package com.pythontopia.stage02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.PythontopiaApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class Stage02AcceptanceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("pythontopia.security.jwt.secret", () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
  }

  @Autowired TestRestTemplate restTemplate;

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired PasswordEncoder passwordEncoder;

  @Autowired ObjectMapper objectMapper;

  @LocalServerPort int port;

  @Test
  void studentRegistersLogsInAndCreatesWorldSession() {
    registerStudent("stu02");
    String token = login("stu02", "P@ssw0rd1");

    ResponseEntity<Map> session =
        restTemplate.exchange(
            "/api/world/session", HttpMethod.GET, bearer(token, null), Map.class);

    assertThat(session.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(session.getBody()).containsEntry("dayIndex", 1).containsEntry("weather", "SUNNY");
  }

  @Test
  void worldSessionBootstrapIsIdempotentUnderConcurrentRequests() {
    registerStudent("stu02-race");
    String token = login("stu02-race", "P@ssw0rd1");
    Long userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = 'stu02-race'", Long.class);

    var requests =
        IntStream.range(0, 4)
            .mapToObj(
                ignored ->
                    CompletableFuture.supplyAsync(
                        () ->
                            restTemplate.exchange(
                                "/api/world/session",
                                HttpMethod.GET,
                                bearer(token, null),
                                Map.class)))
            .toList();

    for (var request : requests) {
      ResponseEntity<Map> session = request.join();
      assertThat(session.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(session.getBody()).containsEntry("dayIndex", 1).containsEntry("weather", "SUNNY");
    }
    Integer sessionRows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM world_session WHERE user_id = ?", Integer.class, userId);
    Integer playerRows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM player_state WHERE user_id = ?", Integer.class, userId);
    assertThat(sessionRows).isEqualTo(1);
    assertThat(playerRows).isEqualTo(1);
  }

  @Test
  void adminCanUseWorldSessionAndEightDirectionMovement() throws Exception {
    insertAdmin("admin02");
    String token = login("admin02", "P@ssw0rd1");

    ResponseEntity<Map> session =
        restTemplate.exchange(
            "/api/world/session", HttpMethod.GET, bearer(token, null), Map.class);
    assertThat(session.getStatusCode()).isEqualTo(HttpStatus.OK);

    RawResponse player =
        patchJsonRaw(
            "/api/world/player",
            token,
            Map.of("tileX", 16, "tileY", 10, "direction", "UP_RIGHT"));
    assertThat(player.status()).isEqualTo(200);
    assertThat(player.body()).containsEntry("tileX", 16).containsEntry("tileY", 10);
    assertThat(player.body()).containsEntry("direction", "UP_RIGHT");
  }

  @Test
  void teacherCreatesClassroomAndStudentJoins() {
    registerTeacher("teacher02");
    registerStudent("stu03");
    String teacherToken = login("teacher02", "P@ssw0rd1");
    String studentToken = login("stu03", "P@ssw0rd1");

    ResponseEntity<Map> classroom =
        restTemplate.exchange(
            "/api/classrooms",
            HttpMethod.POST,
            bearer(teacherToken, Map.of("name", "Python 一班", "description", "入门班")),
            Map.class);
    assertThat(classroom.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String joinCode = (String) classroom.getBody().get("joinCode");
    Number classroomId = (Number) classroom.getBody().get("classroomId");

    ResponseEntity<Map> joined =
        restTemplate.exchange(
            "/api/classrooms/join",
            HttpMethod.POST,
            bearer(studentToken, Map.of("joinCode", joinCode)),
            Map.class);
    assertThat(joined.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map[]> members =
        restTemplate.exchange(
            "/api/classrooms/" + classroomId + "/members",
            HttpMethod.GET,
            bearer(teacherToken, null),
            Map[].class);
    assertThat(members.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(members.getBody()).hasSize(1);
    assertThat(members.getBody()[0]).containsEntry("username", "stu03");

    ResponseEntity<Map> duplicateJoin =
        restTemplate.exchange(
            "/api/classrooms/join",
            HttpMethod.POST,
            bearer(studentToken, Map.of("joinCode", joinCode)),
            Map.class);
    assertThat(duplicateJoin.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(duplicateJoin.getBody()).containsEntry("code", "CLASSROOM_ALREADY_JOINED");
  }

  @Test
  void stage02AuthorizationAndProfileAcceptance() {
    registerStudent("stu04");
    String studentToken = login("stu04", "P@ssw0rd1");

    ResponseEntity<Map> anonymousWorld =
        restTemplate.getForEntity("/api/world/session", Map.class);
    assertThat(anonymousWorld.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(anonymousWorld.getBody()).containsEntry("code", "UNAUTHORIZED");

    ResponseEntity<Map> studentCreateClassroom =
        restTemplate.exchange(
            "/api/classrooms",
            HttpMethod.POST,
            bearer(studentToken, Map.of("name", "应该403")),
            Map.class);
    assertThat(studentCreateClassroom.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(studentCreateClassroom.getBody()).containsEntry("code", "FORBIDDEN");

    ResponseEntity<Void> missingProfile =
        restTemplate.exchange(
            "/api/learner/profile", HttpMethod.GET, bearer(studentToken, null), Void.class);
    assertThat(missingProfile.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void authValidationAndDatabaseConstraintsHold() throws Exception {
    registerStudent("stu05");
    registerTeacher("teacher05");

    ResponseEntity<Map> duplicate =
        restTemplate.postForEntity(
            "/api/auth/register",
            Map.of(
                "username", "stu05",
                "password", "P@ssw0rd1",
                "displayName", "重复",
                "role", "STUDENT"),
            Map.class);
    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    RawResponse badPassword =
        postJsonRaw("/api/auth/login", Map.of("username", "stu05", "password", "wrong-password"));
    assertThat(badPassword.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(badPassword.body()).containsEntry("code", "BAD_CREDENTIALS");

    ResponseEntity<Map> missingInvite =
        restTemplate.postForEntity(
            "/api/auth/register",
            Map.of(
                "username", "teacher-missing-code",
                "password", "P@ssw0rd1",
                "displayName", "教师",
                "role", "TEACHER"),
            Map.class);
    assertThat(missingInvite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO users (username, display_name, password_hash, role)
                    VALUES ('bad-role', 'Bad', 'hash', 'ROOT')
                    """))
        .isInstanceOf(RuntimeException.class);

    Long teacherId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = 'teacher05'", Long.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO classrooms (name, teacher_id, pack_id, join_code)
                    VALUES ('bad-pack', ?, 44, 'BAD044')
                    """,
                    teacherId))
        .isInstanceOf(RuntimeException.class);
  }

  private void registerStudent(String username) {
    ResponseEntity<Map> response =
        restTemplate.postForEntity(
            "/api/auth/register",
            Map.of(
                "username", username,
                "password", "P@ssw0rd1",
                "displayName", username,
                "role", "STUDENT"),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).doesNotContainKeys("passwordHash", "email");
  }

  private void registerTeacher(String username) {
    ResponseEntity<Map> response =
        restTemplate.postForEntity(
            "/api/auth/register",
            Map.of(
                "username", username,
                "password", "P@ssw0rd1",
                "displayName", username,
                "role", "TEACHER",
                "teacherInviteCode", "test-teacher-code"),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private void insertAdmin(String username) {
    jdbcTemplate.update(
        """
        INSERT INTO users (username, display_name, password_hash, role)
        VALUES (?, ?, ?, 'ADMIN')
        """,
        username,
        username,
        passwordEncoder.encode("P@ssw0rd1"));
  }

  private String login(String username, String password) {
    ResponseEntity<Map> response =
        restTemplate.postForEntity(
            "/api/auth/login", Map.of("username", username, "password", password), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return (String) response.getBody().get("token");
  }

  private HttpEntity<?> bearer(String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(body, headers);
  }

  private RawResponse postJsonRaw(String path, Object body) throws Exception {
    String json = objectMapper.writeValueAsString(body);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    return new RawResponse(response.statusCode(), objectMapper.readValue(response.body(), Map.class));
  }

  private RawResponse patchJsonRaw(String path, String bearerToken, Object body) throws Exception {
    String json = objectMapper.writeValueAsString(body);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    return new RawResponse(response.statusCode(), objectMapper.readValue(response.body(), Map.class));
  }

  private record RawResponse(int status, Map<String, Object> body) {}
}
