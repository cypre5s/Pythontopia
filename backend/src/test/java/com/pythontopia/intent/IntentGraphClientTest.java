package com.pythontopia.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.IntentRouteResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

/**
 * 验证 {@link IntentGraphClient} 的字段映射（契约见百晓深度优化蓝本 §0）：
 *
 * <ul>
 *   <li>请求 camelCase → snake_case：recent_turns / last_intent / last_kc_codes /
 *       session_corrections / scope / profile_brief；可选字段为空时不写入 body，scope 缺省 default；
 *   <li>响应 snake_case → camelCase：target / secondary_intent 空安全映射（null→""/null 保持）；
 *   <li>graph 无 intent 时兜底 ASK。
 * </ul>
 */
class IntentGraphClientTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final IntentGraphClient client = new IntentGraphClient(restTemplate, properties);

  private static IntentRouteRequest fullRequest() {
    return new IntentRouteRequest(
        "再来几道",
        List.of("PY43-KC-007"),
        List.of(new IntentRouteRequest.CourseKc("PY43-KC-007", "for 循环")),
        3,
        List.of("PY43-KC-001"),
        List.of(
            new IntentRouteRequest.Turn("user", "给我出几道 for 循环的题"),
            new IntentRouteRequest.Turn("assistant", "已生成 3 道练习")),
        "PRACTICE",
        List.of("PY43-KC-007"),
        List.of(new IntentRouteRequest.Correction("不是想要这个", "MINDMAP")),
        "quiz");
  }

  private static IntentRouteRequest minimalRequest() {
    return new IntentRouteRequest("怎么去闯关塔", null, null, null, null, null, null, null, null, null);
  }

  @Test
  void postsSnakeCaseBodyWithMultiTurnFieldsAndMapsSecondaryIntent() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/intent/route"))
        .andExpect(MockRestRequestMatchers.header("X-Internal-Token", "secret"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.message").value("再来几道"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.recent_turns[0].role").value("user"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.recent_turns[0].content")
                .value("给我出几道 for 循环的题"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.recent_turns[1].role").value("assistant"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.last_intent").value("PRACTICE"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.last_kc_codes[0]").value("PY43-KC-007"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.session_corrections[0].message")
                .value("不是想要这个"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.session_corrections[0].corrected_intent")
                .value("MINDMAP"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.scope").value("quiz"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.profile_brief").value("认知风格: visual; 情绪: 需鼓励"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "intent": "PRACTICE",
                  "kc_codes": ["PY43-KC-007"],
                  "focus_hint": "for 循环",
                  "confidence": 0.86,
                  "reason": "沿用上轮意图",
                  "target": "",
                  "secondary_intent": "MINDMAP"
                }
                """,
                MediaType.APPLICATION_JSON));

    IntentRouteResponse response = client.route(fullRequest(), "认知风格: visual; 情绪: 需鼓励");

    assertThat(response.intent()).isEqualTo("PRACTICE");
    assertThat(response.kcCodes()).containsExactly("PY43-KC-007");
    assertThat(response.focusHint()).isEqualTo("for 循环");
    assertThat(response.confidence()).isEqualTo(0.86);
    assertThat(response.target()).isEmpty();
    assertThat(response.secondaryIntent()).isEqualTo("MINDMAP");
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void omitsEmptyOptionalFieldsDefaultsScopeAndMapsNavigateTarget() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/intent/route"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.recent_turns").doesNotExist())
        .andExpect(MockRestRequestMatchers.jsonPath("$.last_intent").doesNotExist())
        .andExpect(MockRestRequestMatchers.jsonPath("$.last_kc_codes").doesNotExist())
        .andExpect(MockRestRequestMatchers.jsonPath("$.session_corrections").doesNotExist())
        .andExpect(MockRestRequestMatchers.jsonPath("$.profile_brief").doesNotExist())
        .andExpect(MockRestRequestMatchers.jsonPath("$.scope").value("default"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "intent": "NAVIGATE",
                  "kc_codes": [],
                  "focus_hint": "",
                  "confidence": 0.9,
                  "reason": "带路",
                  "target": "arena",
                  "secondary_intent": null
                }
                """,
                MediaType.APPLICATION_JSON));

    IntentRouteResponse response = client.route(minimalRequest(), "");

    assertThat(response.intent()).isEqualTo("NAVIGATE");
    assertThat(response.target()).isEqualTo("arena");
    assertThat(response.secondaryIntent()).isNull();
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void missingResponseFieldsMapNullSafely() {
    // 旧版 graph 不带 target/secondary_intent 等字段时：target→""、secondaryIntent 保持 null。
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/intent/route"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {"intent": "ASK"}
                """,
                MediaType.APPLICATION_JSON));

    IntentRouteResponse response = client.route(minimalRequest(), null);

    assertThat(response.intent()).isEqualTo("ASK");
    assertThat(response.kcCodes()).isEmpty();
    assertThat(response.focusHint()).isEmpty();
    assertThat(response.reason()).isEmpty();
    assertThat(response.target()).isEmpty();
    assertThat(response.secondaryIntent()).isNull();
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void fallsBackToAskWhenGraphOmitsIntent() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/intent/route"))
        .andRespond(MockRestResponseCreators.withSuccess("{}", MediaType.APPLICATION_JSON));

    IntentRouteResponse response = client.route(minimalRequest(), "");

    assertThat(response.intent()).isEqualTo("ASK");
    assertThat(response.reason()).isEqualTo("graph 无响应");
    assertThat(response.target()).isEmpty();
    assertThat(response.secondaryIntent()).isNull();
    server.verify(Duration.ofSeconds(2));
  }
}
