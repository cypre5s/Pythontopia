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
 *       session_corrections / scope / source / profile_brief；可选字段为空时不写入 body，scope 缺省
 *       default、source 缺省 composer_text；
 *   <li>响应 snake_case → camelCase：target / secondary_intent / route plan 空安全映射；
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
        "quiz",
        "capability_launcher",
        "practice",
        "PRACTICE",
        new IntentRouteRequest.InteractionContext(
            "workshop-session-1",
            "town_hub",
            "practice",
            41L,
            "for i in range(n + 1):\n    total += i",
            901L,
            List.of(22L),
            "WA: expected 10, got 15",
            "submit",
            77L));
  }

  private static IntentRouteRequest minimalRequest() {
    return new IntentRouteRequest(
        "怎么去闯关塔", null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private static LearnerRoutingContext learnerContext() {
    return new LearnerRoutingContext(
        "认知风格: visual; 情绪: 需鼓励",
        java.util.Map.of("PY43-KC-007", 0.31),
        java.util.Map.of("PY43-KC-007", 0.8),
        List.of("PY43-KC-007"),
        List.of("PY43-KC-007"),
        "HIGH",
        "HIGH",
        "visual",
        List.of("图解"),
        "CS",
        2,
        List.of(
            new LearnerRoutingContext.RecentResource(
                "PY43-KC-007", "HANDOUT", "2026-07-22T15:00:00Z")),
        List.of(new LearnerRoutingContext.RecentSubmission(41L, "PY43-KC-007", false)),
        List.of("循环边界反复失败"),
        List.of(),
        List.of("短步骤+鼓励反馈"),
        "2026-07-22T15:01:00Z");
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
        .andExpect(MockRestRequestMatchers.jsonPath("$.source").value("capability_launcher"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.capability_namespace").value("practice"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.requested_intent").value("PRACTICE"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.interaction.latest_submission_id").value(901))
        .andExpect(MockRestRequestMatchers.jsonPath("$.interaction.current_problem_id").value(41))
        .andExpect(MockRestRequestMatchers.jsonPath("$.interaction.selected_page_ids[0]").value(22))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.profile_brief").value("认知风格: visual; 情绪: 需鼓励"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.learner_context.cognitive_load").value("HIGH"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.learner_context.kc_mastery.PY43-KC-007")
                .value(0.31))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.learner_context.recent_submissions[0].passed")
                .value(false))
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
                  "secondary_intent": "MINDMAP",
                  "namespace": "practice",
                  "candidate_intents": ["PRACTICE", "CODE_LAB"],
                  "needs_clarification": false,
                  "clarification_prompt": "",
                  "risk_class": "generative",
                  "render_mode": "practice",
                  "reason_code": "capability_continuation",
                  "route_decision": {
                    "route_id": "route_0123456789abcdef",
                    "version": 1,
                    "task": {
                      "family": "PRACTICE",
                      "subtype": "PRACTICE",
                      "complexity": "C1",
                      "confidence": 0.86,
                      "needs_clarification": false,
                      "clarification_question": ""
                    },
                    "orchestration": {
                      "topology": "SINGLE_AGENT_TOOL",
                      "primary_agent": "PracticeAgent",
                      "supporting_agents": []
                    },
                    "tools": {"required": ["PRACTICE"], "optional": ["ASK"], "forbidden": []},
                    "policies": {
                      "max_tool_calls": 8,
                      "max_calls_per_tool": 2,
                      "max_agent_calls": 4,
                      "max_planning_depth": 3,
                      "max_parallel_agents": 3,
                      "max_replans": 1,
                      "require_grounding": true,
                      "require_safety": true,
                      "allow_common_knowledge": false,
                      "read_before_write": true
                    },
                    "output": {
                      "schema_name": "WorkshopPracticeCard",
                      "render_mode": "practice",
                      "require_evidence": true
                    },
                    "fallback": {
                      "on_low_confidence": "ASK_CLARIFICATION",
                      "on_missing_context": "ASK_CLARIFICATION",
                      "on_no_evidence": "DISCLOSE_COMMON_KNOWLEDGE",
                      "on_tool_failure": "RETURN_PARTIAL_WITH_ERROR",
                      "on_model_failure": "USE_DETERMINISTIC_FALLBACK",
                      "on_guard_failure": "SAFE_TEMPLATE"
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    IntentRouteResponse response =
        client.route(
            fullRequest(), "认知风格: visual; 情绪: 需鼓励", learnerContext());

    assertThat(response.intent()).isEqualTo("PRACTICE");
    assertThat(response.kcCodes()).containsExactly("PY43-KC-007");
    assertThat(response.focusHint()).isEqualTo("for 循环");
    assertThat(response.confidence()).isEqualTo(0.86);
    assertThat(response.target()).isEmpty();
    assertThat(response.secondaryIntent()).isEqualTo("MINDMAP");
    assertThat(response.namespace()).isEqualTo("practice");
    assertThat(response.candidateIntents()).containsExactly("PRACTICE", "CODE_LAB");
    assertThat(response.needsClarification()).isFalse();
    assertThat(response.clarificationPrompt()).isEmpty();
    assertThat(response.riskClass()).isEqualTo("generative");
    assertThat(response.renderMode()).isEqualTo("practice");
    assertThat(response.reasonCode()).isEqualTo("capability_continuation");
    assertThat(response.routeDecision().routeId()).isEqualTo("route_0123456789abcdef");
    assertThat(response.routeDecision().task().family()).isEqualTo("PRACTICE");
    assertThat(response.routeDecision().orchestration().primaryAgent())
        .isEqualTo("PracticeAgent");
    assertThat(response.routeDecision().tools().required()).containsExactly("PRACTICE");
    assertThat(response.routeDecision().policies().maxToolCalls()).isEqualTo(8);
    assertThat(response.routeDecision().search()).isNull();
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void mapsC3SearchContractSnakeToCamelWithoutInventingFields() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/intent/route"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "intent": "HANDOUT",
                  "kc_codes": ["PY43-KC-031"],
                  "focus_hint": "循环结构",
                  "confidence": 0.94,
                  "reason": "三动作复合请求",
                  "secondary_intent": "PRACTICE",
                  "namespace": "workshop",
                  "candidate_intents": ["HANDOUT", "PRACTICE", "PLAN"],
                  "needs_clarification": false,
                  "clarification_prompt": "",
                  "risk_class": "generative",
                  "render_mode": "resource",
                  "reason_code": "multi_intent",
                  "route_decision": {
                    "route_id": "route_00c3c3c3c3c3c3c3",
                    "version": 1,
                    "task": {
                      "family": "MULTI_TASK",
                      "subtype": "compound",
                      "sub_intents": ["HANDOUT", "PRACTICE", "PLAN"],
                      "complexity": "C3",
                      "confidence": 0.94,
                      "needs_clarification": false,
                      "clarification_question": ""
                    },
                    "orchestration": {
                      "topology": "PLANNER_EXECUTOR",
                      "primary_agent": "PathPlannerAgent",
                      "supporting_agents": ["HandoutAgent", "PracticeAgent"]
                    },
                    "tools": {
                      "required": ["HANDOUT", "PRACTICE", "PLAN"],
                      "optional": [],
                      "forbidden": []
                    },
                    "policies": {
                      "max_tool_calls": 8,
                      "max_calls_per_tool": 2,
                      "max_agent_calls": 4,
                      "max_planning_depth": 3,
                      "max_parallel_agents": 1,
                      "max_replans": 1,
                      "require_grounding": true,
                      "require_safety": true,
                      "allow_common_knowledge": false,
                      "read_before_write": true
                    },
                    "output": {
                      "schema_name": "WorkshopCompoundResult",
                      "render_mode": "compound",
                      "require_evidence": true
                    },
                    "fallback": {
                      "on_low_confidence": "ASK_CLARIFICATION",
                      "on_missing_context": "ASK_CLARIFICATION",
                      "on_no_evidence": "DISCLOSE_COMMON_KNOWLEDGE",
                      "on_tool_failure": "RETURN_PARTIAL_WITH_ERROR",
                      "on_model_failure": "USE_DETERMINISTIC_FALLBACK",
                      "on_guard_failure": "SAFE_TEMPLATE"
                    },
                    "search": {
                      "strategy": "BOUNDED_TOT_ASTAR",
                      "candidates": [
                        {
                          "candidate_id": "candidate_plan_first",
                          "label": "规划诊断优先",
                          "agent_path": ["PathPlannerAgent", "HandoutAgent", "PracticeAgent"],
                          "tool_path": ["PLAN", "HANDOUT", "PRACTICE"],
                          "depth": 3,
                          "g_cost": 2.66,
                          "h_cost": 0.1,
                          "penalty": 0.0,
                          "total_cost": 2.76
                        },
                        {
                          "candidate_id": "candidate_rebuild_first",
                          "label": "知识重构优先",
                          "agent_path": ["HandoutAgent", "PracticeAgent", "PathPlannerAgent"],
                          "tool_path": ["HANDOUT", "PRACTICE", "PLAN"],
                          "depth": 3,
                          "g_cost": 2.63,
                          "h_cost": 0.15,
                          "penalty": 0.15,
                          "total_cost": 2.93
                        },
                        {
                          "candidate_id": "candidate_practice_first",
                          "label": "练习探测优先",
                          "agent_path": ["PracticeAgent", "HandoutAgent", "PathPlannerAgent"],
                          "tool_path": ["PRACTICE", "HANDOUT", "PLAN"],
                          "depth": 3,
                          "g_cost": 2.62,
                          "h_cost": 0.15,
                          "penalty": 0.75,
                          "total_cost": 3.52
                        }
                      ],
                      "selected_candidate_id": "candidate_plan_first",
                      "max_depth": 3,
                      "score_formula": "total_cost=g_cost+h_cost+penalty",
                      "replan_policy": {"max_candidate_switches": 1}
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    IntentRouteResponse response = client.route(minimalRequest(), "");

    var search = response.routeDecision().search();
    assertThat(search).isNotNull();
    assertThat(search.strategy()).isEqualTo("BOUNDED_TOT_ASTAR");
    assertThat(search.selectedCandidateId()).isEqualTo("candidate_plan_first");
    assertThat(search.maxDepth()).isEqualTo(3);
    assertThat(search.scoreFormula()).isEqualTo("total_cost=g_cost+h_cost+penalty");
    assertThat(search.replanPolicy().maxCandidateSwitches()).isEqualTo(1);
    assertThat(search.candidates()).hasSize(3);
    var first = search.candidates().get(0);
    assertThat(first.candidateId()).isEqualTo("candidate_plan_first");
    assertThat(first.label()).isEqualTo("规划诊断优先");
    assertThat(first.agentPath())
        .containsExactly("PathPlannerAgent", "HandoutAgent", "PracticeAgent");
    assertThat(first.toolPath()).containsExactly("PLAN", "HANDOUT", "PRACTICE");
    assertThat(first.depth()).isEqualTo(3);
    assertThat(first.gCost()).isEqualTo(2.66);
    assertThat(first.hCost()).isEqualTo(0.1);
    assertThat(first.penalty()).isEqualTo(0.0);
    assertThat(first.totalCost()).isEqualTo(2.76);
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
        .andExpect(MockRestRequestMatchers.jsonPath("$.source").value("composer_text"))
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
    assertThat(response.namespace()).isEqualTo("ask");
    assertThat(response.candidateIntents()).containsExactly("ASK");
    assertThat(response.needsClarification()).isTrue();
    assertThat(response.clarificationPrompt()).contains("受审计决策");
    assertThat(response.riskClass()).isEqualTo("generative");
    assertThat(response.renderMode()).isEqualTo("clarification");
    assertThat(response.reasonCode()).isEqualTo("legacy_response");
    assertThat(response.routeDecision()).isNull();
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void browseIntentFallbackMetadataUsesFindReadOnlyBrowseRenderer() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/intent/route"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {"intent": "BROWSE", "focus_hint": "Python sorted stability"}
                """,
                MediaType.APPLICATION_JSON));

    IntentRouteResponse response = client.route(minimalRequest(), null);

    assertThat(response.intent()).isEqualTo("BROWSE");
    assertThat(response.focusHint()).isEqualTo("Python sorted stability");
    assertThat(response.namespace()).isEqualTo("find");
    assertThat(response.riskClass()).isEqualTo("read_only");
    assertThat(response.renderMode()).isEqualTo("clarification");
    assertThat(response.candidateIntents()).containsExactly("BROWSE");
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
    assertThat(response.namespace()).isEqualTo("ask");
    assertThat(response.candidateIntents()).containsExactly("ASK");
    assertThat(response.needsClarification()).isTrue();
    assertThat(response.riskClass()).isEqualTo("generative");
    assertThat(response.renderMode()).isEqualTo("clarification");
    assertThat(response.reasonCode()).isEqualTo("graph_unavailable");
    server.verify(Duration.ofSeconds(2));
  }
}
