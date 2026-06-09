package com.pythontopia.learner.decision;

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

/**
 * P7.1：决策玻璃盒后端数据 + 端点测试。验证 GET /api/learner/decisions 从 resource_run surface 出真实
 * 决策类型（problem/path/resource …），按 recency 倒序、仅本人，且每条携带 agent + 决策类型 + 结论 + 证据页链。
 */
@Testcontainers
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DecisionTraceControllerTest {

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

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @SuppressWarnings("unchecked")
  void surfacesDecisionKindsScopedAndOrdered() {
    String token = registerAndLogin("glassbox-stu");
    Long userId = userId("glassbox-stu");
    Long otherUserId =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES ('glassbox-other', 'o', 'x', 'STUDENT') RETURNING id",
            Long.class);

    seedRun(userId, "PracticeAgent", "RESOURCE_PRACTICE", "SUCCESS", "ARRAY[101,102]::bigint[]", 3);
    seedRun(userId, "CurriculumMapperAgent", "CURRICULUM_BOOTSTRAP", "SUCCESS", "ARRAY[]::bigint[]", 2);
    seedRun(
        userId,
        "ResourceDesignerAgent.handout",
        "RESOURCE_HANDOUT",
        "SUCCESS",
        "ARRAY[201]::bigint[]",
        1);
    // 跨用户行：必须不出现在本人玻璃盒中。
    seedRun(otherUserId, "PracticeAgent", "RESOURCE_PRACTICE", "SUCCESS", "ARRAY[]::bigint[]", 1);

    ResponseEntity<List> response =
        restTemplate.exchange(
            "/api/learner/decisions?limit=10", HttpMethod.GET, bearer(token), List.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<Map<String, Object>> traces = response.getBody();
    assertThat(traces).hasSize(3); // 仅本人，跨用户被排除

    // recency 倒序：最新（1 分钟前）的 RESOURCE_HANDOUT 在最前。
    assertThat(traces.get(0).get("decision_kind")).isEqualTo("resource");
    assertThat(traces.get(1).get("decision_kind")).isEqualTo("path");
    assertThat(traces.get(2).get("decision_kind")).isEqualTo("problem");

    assertThat(traces.stream().map(t -> t.get("decision_kind")).toList())
        .contains("problem", "path", "resource");

    Map<String, Object> problem =
        traces.stream()
            .filter(t -> "problem".equals(t.get("decision_kind")))
            .findFirst()
            .orElseThrow();
    assertThat(problem.get("agent_name")).isEqualTo("PracticeAgent");
    assertThat(problem.get("conclusion")).asString().isNotBlank();
    assertThat((List<Object>) problem.get("evidence_page_ids")).hasSize(2);
  }

  private void seedRun(
      Long userId,
      String agent,
      String scenario,
      String status,
      String evidenceArraySql,
      int minutesAgo) {
    jdbcTemplate.update(
        "INSERT INTO resource_run (user_id, agent_name, scenario, status, evidence_page_ids, created_at)"
            + " VALUES (?, ?, ?, ?, "
            + evidenceArraySql
            + ", now() - (? * interval '1 minute'))",
        userId,
        agent,
        scenario,
        status,
        minutesAgo);
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
            "/api/auth/login", Map.of("username", username, "password", "P@ssw0rd1"), Map.class);
    return (String) response.getBody().get("token");
  }

  private HttpEntity<?> bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(null, headers);
  }
}
