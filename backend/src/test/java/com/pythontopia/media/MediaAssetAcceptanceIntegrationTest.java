package com.pythontopia.media;

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
 * 端到端验证多模态媒资 FE↔BE 链路（真实 PG + Spring MVC + Security）：
 * 文生图（无 key 占位降级）→ media_asset 落库 → 匿名按 id 取流 → 8 维画像维度端点。
 */
@Testcontainers
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MediaAssetAcceptanceIntegrationTest {

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
    registry.add(
        "pythontopia.media.generatedDir",
        () -> System.getProperty("java.io.tmpdir") + "/pyt-media-it");
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @SuppressWarnings("unchecked")
  void generateImageFallsBackToPlaceholderAndIsServedAnonymously() {
    String token = registerAndLogin("media-stu01");
    Long userId = userId("media-stu01");

    ResponseEntityBody image =
        post(
            "/api/media/image",
            token,
            Map.of("prompt", "for 循环执行流程图解", "label", "PY43-KC-030", "kcCodes", List.of("PY43-KC-030")));
    assertThat(image.status).isEqualTo(HttpStatus.OK);
    assertThat(image.body.get("kind")).isEqualTo("IMAGE");
    // 测试环境无讯飞 key → 走本地占位降级。
    assertThat(image.body.get("degraded")).isEqualTo(true);
    assertThat(image.body.get("source")).isEqualTo("PLACEHOLDER");
    String url = (String) image.body.get("url");
    assertThat(url).startsWith("/api/media/assets/");
    Long assetId = ((Number) image.body.get("id")).longValue();

    Integer rows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM media_asset WHERE id = ? AND user_id = ? AND kind = 'IMAGE'",
            Integer.class,
            assetId,
            userId);
    assertThat(rows).isEqualTo(1);

    // 匿名取流（<img src> 不带 Authorization）：permitAll 放行，返回真实 PNG 字节。
    ResponseEntity<byte[]> served =
        restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, byte[].class);
    assertThat(served.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(served.getBody()).isNotEmpty();
    assertThat(served.getHeaders().getContentType().toString()).contains("image/png");
  }

  @Test
  @SuppressWarnings("unchecked")
  void dimensionsEndpointReturnsEightDimensions() {
    String token = registerAndLogin("media-stu02");
    ResponseEntity<List> response =
        restTemplate.exchange(
            "/api/learner/profile/dimensions", HttpMethod.GET, bearer(token, null), List.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(8);
    Map<String, Object> first = (Map<String, Object>) response.getBody().get(0);
    assertThat(first).containsKeys("key", "label", "score", "selfEditable");
  }

  private record ResponseEntityBody(HttpStatus status, Map<String, Object> body) {}

  @SuppressWarnings("unchecked")
  private ResponseEntityBody post(String path, String token, Object body) {
    ResponseEntity<Map> response =
        restTemplate.exchange(path, HttpMethod.POST, bearer(token, body), Map.class);
    return new ResponseEntityBody(
        HttpStatus.valueOf(response.getStatusCode().value()), response.getBody());
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
