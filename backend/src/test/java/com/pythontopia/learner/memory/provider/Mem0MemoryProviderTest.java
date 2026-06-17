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
 * Mem0 provider 单测（测试矩阵 ②「mem0 provider 正常」+ 故障路径）：用 Spring {@link MockRestServiceServer}
 * 模拟 Mem0 REST，<strong>零外网</strong>（后端 pom 未含 okhttp MockWebServer，沿用本仓既有 HTTP mock 约定，
 * 同样不开真实 socket）。覆盖：召回字段映射、写入请求体与 id 解析、5xx → {@link MemoryProviderException}、
 * 未配置 base url → 不发请求即抛。
 */
class Mem0MemoryProviderTest {

  private static final String BASE = "http://mem0.test";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MemoryProviderProperties props(String baseUrl, String apiKey) {
    return new MemoryProviderProperties("mem0", baseUrl, apiKey, "", "", 2000, 4000);
  }

  @Test
  void recallMapsResultsToRecalledMemory() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo(BASE + "/v1/memories/search/"))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.header(HttpHeaders.AUTHORIZATION, "Token KEY"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.query").value("列表 append"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.user_id").value("7"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.limit").value(8))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "results": [
                    {"id": "m1", "memory": "你常混淆 append 和 extend", "score": 0.91,
                     "created_at": "2026-06-12T00:00:00Z",
                     "metadata": {"kc_code": "PY43-KC-016", "kind": "weak_kc"}},
                    {"id": "m2", "memory": "偏好看视频讲解", "score": 0.70,
                     "created_at": "2026-06-10T00:00:00Z",
                     "metadata": {"kind": "preference"}}
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    Mem0MemoryProvider provider =
        new Mem0MemoryProvider(restTemplate, objectMapper, props(BASE, "KEY"));

    List<RecalledMemory> recalled = provider.recall(7L, "列表 append", 8);

    assertThat(recalled).hasSize(2);
    RecalledMemory first = recalled.get(0);
    assertThat(first.text()).isEqualTo("你常混淆 append 和 extend");
    assertThat(first.kcCode()).isEqualTo("PY43-KC-016");
    assertThat(first.kind()).isEqualTo("weak_kc");
    assertThat(first.score()).isEqualTo(0.91);
    assertThat(first.source()).isEqualTo("mem0");
    assertThat(first.occurredAt()).isEqualTo(Instant.parse("2026-06-12T00:00:00Z"));
    assertThat(first.isStruggle()).isTrue();
    // 第二条无 kc_code / 用默认 kind=fact? 其 metadata.kind=preference。
    assertThat(recalled.get(1).kcCode()).isNull();
    assertThat(recalled.get(1).kind()).isEqualTo("preference");
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void rememberPostsMessagesAndParsesMemoryId() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo(BASE + "/v1/memories/"))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.header(HttpHeaders.AUTHORIZATION, "Token KEY"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.user_id").value("7"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.messages[0].role").value("user"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.messages[0].content").value("通过了 for 循环练习"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.metadata.kind").value("fact"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.metadata.kc_code").value("PY43-KC-007"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {"results": [{"id": "mem-xyz", "memory": "通过了 for 循环练习", "event": "ADD"}]}
                """,
                MediaType.APPLICATION_JSON));

    Mem0MemoryProvider provider =
        new Mem0MemoryProvider(restTemplate, objectMapper, props(BASE, "KEY"));

    MemoryRef ref = provider.remember(7L, MemoryEvent.of("fact", "通过了 for 循环练习", "PY43-KC-007"));

    assertThat(ref.provider()).isEqualTo("mem0");
    assertThat(ref.externalId()).isEqualTo("mem-xyz");
    assertThat(ref.kind()).isEqualTo("fact");
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void serverErrorRaisesMemoryProviderException() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo(BASE + "/v1/memories/search/"))
        .andRespond(MockRestResponseCreators.withServerError());

    Mem0MemoryProvider provider =
        new Mem0MemoryProvider(restTemplate, objectMapper, props(BASE, "KEY"));

    assertThatThrownBy(() -> provider.recall(7L, "x", 5))
        .isInstanceOf(MemoryProviderException.class);
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void missingBaseUrlFailsFastWithoutRequest() {
    RestTemplate restTemplate = new RestTemplate();
    // 不设置任何期望：若发起请求会判失败。base url 缺失应在发请求前抛出。
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    Mem0MemoryProvider provider =
        new Mem0MemoryProvider(restTemplate, objectMapper, props("", "KEY"));

    assertThatThrownBy(() -> provider.recall(7L, "x", 5))
        .isInstanceOf(MemoryProviderException.class);
    server.verify(Duration.ofSeconds(2));
  }
}
