package com.pythontopia.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * B站视频检索后端端到端验收（真实 PG + Spring Security + 真实 pythontopia-graph）。
 *
 * <p>依赖开发栈 graph 在 {@code PYT_GRAPH_E2E_URL}（默认 {@code http://127.0.0.1:18091}）可访问；
 * graph 未起时整条类跳过（Assumptions），避免 CI 无 graph 时误红。
 */
@Testcontainers
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BilibiliVideoAcceptanceIntegrationTest {

  private static final String GRAPH_E2E_URL =
      System.getenv().getOrDefault("PYT_GRAPH_E2E_URL", "http://127.0.0.1:18091");

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
    registry.add("pythontopia.services.graphUrl", () -> GRAPH_E2E_URL);
    registry.add("pythontopia.services.ragUrl", () -> "http://127.0.0.1:18092");
    registry.add("pythontopia.services.ragInternalSecret", () -> "e2e-secret");
  }

  @Autowired TestRestTemplate restTemplate;

  @LocalServerPort int port;

  @BeforeAll
  static void requireLiveGraph() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(GRAPH_E2E_URL + "/healthz"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException exception) {
      Assumptions.assumeTrue(
          false,
          () -> "graph 未就绪（" + GRAPH_E2E_URL + "），跳过 B站 e2e: " + exception.getMessage());
      return;
    }
    Assumptions.assumeTrue(
        response.statusCode() == 200,
        () -> "graph 未就绪（" + GRAPH_E2E_URL + "），跳过 B站 e2e");
  }

  @Test
  void unauthenticatedSearchReturns401() {
    ResponseEntity<Map> response =
        restTemplate.getForEntity(
            "/api/videos/bilibili?keyword=for循环%20Python&limit=3", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @SuppressWarnings("unchecked")
  void authenticatedSearchReturnsRealBilibiliCards() {
    String token = registerAndLogin("bili-e2e-stu");

    ResponseEntity<List> response =
        restTemplate.exchange(
            "/api/videos/bilibili?keyword=for循环%20Python&limit=3",
            HttpMethod.GET,
            bearer(token, null),
            List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull().isNotEmpty();

    Map<String, Object> first = (Map<String, Object>) response.getBody().get(0);
    assertThat(first)
        .containsKeys(
            "bvid", "title", "author", "description", "coverUrl", "durationSec", "play", "url");
    assertThat((String) first.get("bvid")).startsWith("BV");
    assertThat((String) first.get("coverUrl")).startsWith("https://");
    assertThat((String) first.get("url")).contains("bilibili.com/video/");
    assertThat(((Number) first.get("durationSec")).intValue()).isGreaterThanOrEqualTo(0);
  }

  @Test
  @SuppressWarnings("unchecked")
  void videoIntentRouteReturnsVideoForNaturalLanguage() {
    String token = registerAndLogin("bili-e2e-intent");

    Map<String, Object> body =
        Map.of(
            "message",
            "我想看 for 循环的视频讲解",
            "manualKcCodes",
            List.of(),
            "courseKcs",
            List.of(Map.of("code", "PY43-KC-030", "name", "for循环")),
            "recentKcCodes",
            List.of());

    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/intent/route", HttpMethod.POST, bearer(token, body), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("intent")).isEqualTo("VIDEO");
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
