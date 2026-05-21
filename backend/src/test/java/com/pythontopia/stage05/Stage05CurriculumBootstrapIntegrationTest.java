package com.pythontopia.stage05;

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
class Stage05CurriculumBootstrapIntegrationTest {

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
  void bootstrapWithoutProfileReturnsConflict() {
    String token = registerAndLogin("field-stu01");
    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/learner/curriculum/bootstrap",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("code", "PROFILE_REQUIRED");
  }

  @Test
  void bootstrapCreatesAllTilesAndUnlocksDefaultEight() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("field-stu02");
    Long userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = ?", Long.class, "field-stu02");
    seedProfile(userId);

    graphServer
        .expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/agents/curriculum/map"))
        .andRespond(withJson(graphResponse()));

    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/learner/curriculum/bootstrap",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(((Number) response.getBody().get("totalKcs")).intValue()).isEqualTo(65);
    assertThat(((Number) response.getBody().get("unlockedCount")).intValue()).isEqualTo(8);

    Integer total =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM crop_tile_state WHERE user_id = ?", Integer.class, userId);
    Integer unlocked =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM crop_tile_state WHERE user_id = ? AND unlocked = TRUE",
            Integer.class,
            userId);
    assertThat(total).isEqualTo(65);
    assertThat(unlocked).isEqualTo(8);

    Integer events =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ?", Integer.class, userId);
    assertThat(events).isGreaterThanOrEqualTo(8);

    Integer runs =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_run WHERE user_id = ? AND agent_name = 'CurriculumMapperAgent' AND status = 'SUCCESS'",
            Integer.class,
            userId);
    assertThat(runs).isEqualTo(1);

    ResponseEntity<List> crops =
        restTemplate.exchange(
            "/api/world/crops", HttpMethod.GET, bearer(token, null), List.class);
    assertThat(crops.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(crops.getBody()).hasSize(65);

    ResponseEntity<List> mastery =
        restTemplate.exchange(
            "/api/learner/mastery", HttpMethod.GET, bearer(token, null), List.class);
    assertThat(mastery.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(mastery.getBody()).hasSizeGreaterThanOrEqualTo(8);

    graphServer.verify(Duration.ofSeconds(2));
  }

  private String graphResponse() throws Exception {
    List<Map<String, Object>> tiles = new java.util.ArrayList<>();
    List<Map<String, Object>> planSteps = new java.util.ArrayList<>();
    List<String> kcCodes =
        jdbcTemplate.queryForList(
            "SELECT kc_code FROM course_kc WHERE pack_id = 43 ORDER BY chapter_order, id",
            String.class);
    for (int index = 0; index < kcCodes.size(); index++) {
      String code = kcCodes.get(index);
      int tileX = 2 + (index % 5);
      int tileY = 2 + (index / 5);
      tiles.add(
          Map.of("kc_code", code, "tile_x", tileX, "tile_y", tileY, "chapter_id", 0));
      planSteps.add(
          Map.of(
              "order", index + 1,
              "kc_code", code,
              "chapter_order", 1,
              "rationale", "test"));
    }
    Map<String, Object> body =
        Map.of(
            "plan_steps",
            planSteps,
            "tile_assignments",
            tiles,
            "unlocked_kcs",
            kcCodes.subList(0, Math.min(8, kcCodes.size())),
            "deterministic_signature",
            "test-signature");
    return objectMapper.writeValueAsString(body);
  }

  private void seedProfile(Long userId) {
    jdbcTemplate.update(
        """
        INSERT INTO learner_profile (
          user_id, pack_id, major, base_level, learning_goal, cognitive_style,
          learning_preference, error_patterns, practice_capability,
          emotion_persistence, narrative_summary
        )
        VALUES (
          ?, 43, 'CS',
          '{"programming_years":1,"languages_known":["C"],"python_self_rated_level":2}'::jsonb,
          '{"kind":"EXAM","deadline_iso":null,"target_kc_codes":["PY43-KC-001"]}'::jsonb,
          '{"field_dependent":0.4,"sequential_vs_holistic":0.5}'::jsonb,
          '{"media_priority":["code","text"],"session_minutes_per_day":45}'::jsonb,
          '{"frequent_kc_codes":["PY43-KC-001"],"common_categories":["syntax"]}'::jsonb,
          '{"avg_attempts_per_problem":2.0,"success_rate":0.65}'::jsonb,
          '{"resilience":0.7,"recent_mood":"neutral"}'::jsonb,
          '测试画像'
        )
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
