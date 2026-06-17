package com.pythontopia.learner.memory.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

/**
 * Zep provider 单测（时序图召回）：用 {@link MockRestServiceServer} 模拟 Zep graph REST，零外网。覆盖：
 * edges→RecalledMemory 映射（valid_at 优先体现时序）、写入 uuid 解析、5xx → {@link MemoryProviderException}。
 */
class ZepMemoryProviderTest {

  private static final String BASE = "http://zep.test";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MemoryProviderProperties props(String baseUrl, String apiKey) {
    return new MemoryProviderProperties("zep", "", "", baseUrl, apiKey, 2000, 4000);
  }

  @Test
  void recallMapsEdgesToRecalledMemory() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo(BASE + "/api/v2/graph/search"))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.header(HttpHeaders.AUTHORIZATION, "Api-Key ZKEY"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.user_id").value("7"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.query").value("循环"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.scope").value("edges"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "edges": [
                    {"fact": "学生上周开始坚持每日练习", "score": 0.88,
                     "valid_at": "2026-06-15T00:00:00Z", "created_at": "2026-06-01T00:00:00Z",
                     "attributes": {"kc_code": "PY43-KC-007"}},
                    {"fact": "偏好图解胜过文字", "score": 0.6, "created_at": "2026-06-09T00:00:00Z"}
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    ZepMemoryProvider provider =
        new ZepMemoryProvider(restTemplate, objectMapper, props(BASE, "ZKEY"));

    List<RecalledMemory> recalled = provider.recall(7L, "循环", 6);

    assertThat(recalled).hasSize(2);
    RecalledMemory first = recalled.get(0);
    assertThat(first.text()).isEqualTo("学生上周开始坚持每日练习");
    assertThat(first.kcCode()).isEqualTo("PY43-KC-007");
    assertThat(first.source()).isEqualTo("zep");
    // valid_at 优先于 created_at，体现时序事实的有效时间。
    assertThat(first.occurredAt()).isEqualTo(Instant.parse("2026-06-15T00:00:00Z"));
    assertThat(recalled.get(1).occurredAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void rememberParsesUuid() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo(BASE + "/api/v2/graph"))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.jsonPath("$.user_id").value("7"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.type").value("text"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.data").value("通过了列表练习"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {"uuid": "edge-uuid-1", "processed": true}
                """,
                MediaType.APPLICATION_JSON));

    ZepMemoryProvider provider =
        new ZepMemoryProvider(restTemplate, objectMapper, props(BASE, "ZKEY"));

    MemoryRef ref = provider.remember(7L, MemoryEvent.of("fact", "通过了列表练习", null));

    assertThat(ref.provider()).isEqualTo("zep");
    assertThat(ref.externalId()).isEqualTo("edge-uuid-1");
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void serverErrorRaisesMemoryProviderException() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo(BASE + "/api/v2/graph/search"))
        .andRespond(MockRestResponseCreators.withServerError());

    ZepMemoryProvider provider =
        new ZepMemoryProvider(restTemplate, objectMapper, props(BASE, "ZKEY"));

    assertThatThrownBy(() -> provider.recall(7L, "x", 5))
        .isInstanceOf(MemoryProviderException.class);
    server.verify(Duration.ofSeconds(2));
  }
}
