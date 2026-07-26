package com.pythontopia.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.WorkshopRouteDecision;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RouteExecutionTokenSignerTest {

  private static final Instant ISSUED_AT = Instant.parse("2026-07-22T12:00:00Z");

  @Test
  void signedTokenIsBoundToUserAndExactRouteDecision() {
    RouteExecutionTokenSigner signer = signerAt(ISSUED_AT);
    WorkshopRouteDecision decision = multiDecision("route_0123456789abcdef");
    IntentRouteRequest.InteractionContext interaction = interaction(901L);

    String token = signer.issue(7L, decision, "先总结再出题", interaction);

    assertThat(token).isNotBlank();
    assertThat(signer.verify(7L, decision, "先总结再出题", interaction, token)).isTrue();
    assertThat(signer.verify(8L, decision, "先总结再出题", interaction, token)).isFalse();
    assertThat(
            signer.verify(
                7L,
                multiDecision("route_fedcba9876543210"),
                "先总结再出题",
                interaction,
                token))
        .isFalse();
    assertThat(signer.verify(7L, decision, "换一条消息", interaction, token)).isFalse();
    assertThat(signer.verify(7L, decision, "先总结再出题", interaction(902L), token)).isFalse();
    assertThat(signer.verify(7L, decision, "先总结再出题", interaction, token + "x"))
        .isFalse();
  }

  @Test
  void tokenExpiresAfterTenMinutes() {
    WorkshopRouteDecision decision = multiDecision("route_0123456789abcdef");
    String token = signerAt(ISSUED_AT).issue(7L, decision, "先总结再出题", null);

    assertThat(
            signerAt(ISSUED_AT.plusSeconds(600))
                .verify(7L, decision, "先总结再出题", null, token))
        .isTrue();
    assertThat(
            signerAt(ISSUED_AT.plusSeconds(601))
                .verify(7L, decision, "先总结再出题", null, token))
        .isFalse();
  }

  @Test
  void blankShortAndPublishedPlaceholderSecretsFailClosed() {
    WorkshopRouteDecision decision = multiDecision("route_0123456789abcdef");

    assertThat(signerAt(ISSUED_AT, "").issue(7L, decision, "message", null)).isNull();
    assertThat(signerAt(ISSUED_AT, "too-short").issue(7L, decision, "message", null)).isNull();
    assertThat(signerAt(ISSUED_AT, "pytopia-rag-admin").issue(7L, decision, "message", null))
        .isNull();
    assertThat(
            signerAt(ISSUED_AT, "change-me-change-me-change-me-change-me")
                .issue(7L, decision, "message", null))
        .isNull();
  }

  @Test
  void c3SearchContractIsCoveredByExecutionSignature() {
    RouteExecutionTokenSigner signer = signerAt(ISSUED_AT);
    WorkshopRouteDecision decision = c3Decision("route_00c3c3c3c3c3c3c3", "candidate_plan_first");
    String message = "先总结，再根据错误出题，并告诉我先做哪一道";

    String token = signer.issue(7L, decision, message, null);

    assertThat(token).isNotBlank();
    assertThat(signer.verify(7L, decision, message, null, token)).isTrue();
    // 篡改 A* 选中项：同一批候选换 selected → 签名必须失效。
    assertThat(
            signer.verify(
                7L,
                c3Decision("route_00c3c3c3c3c3c3c3", "candidate_rebuild_first"),
                message,
                null,
                token))
        .isFalse();
    // 剥离 search 合同（伪装成未搜索的 C3）→ 签名必须失效。
    assertThat(signer.verify(7L, withoutSearch(decision), message, null, token)).isFalse();
  }

  private static RouteExecutionTokenSigner signerAt(Instant now) {
    return signerAt(now, "e2e-route-secret-0123456789abcdef0123456789abcdef");
  }

  private static RouteExecutionTokenSigner signerAt(Instant now, String secret) {
    return new RouteExecutionTokenSigner(
        new WorkshopRouteSecurityProperties(secret),
        new ObjectMapper(),
        Clock.fixed(now, ZoneOffset.UTC));
  }

  private static IntentRouteRequest.InteractionContext interaction(Long submissionId) {
    return new IntentRouteRequest.InteractionContext(
        "sess", "workshop", null, 41L, "print(1)", submissionId, List.of(), "WA", null, null);
  }

  public static WorkshopRouteDecision multiDecision(String routeId) {
    return new WorkshopRouteDecision(
        routeId,
        1,
        new WorkshopRouteDecision.Task(
            "MULTI_TASK",
            "compound",
            List.of("HANDOUT", "PRACTICE"),
            "C2",
            0.94,
            false,
            ""),
        new WorkshopRouteDecision.Orchestration(
            "SEQUENTIAL", "ResourceDesignerAgent", List.of("PracticeAgent")),
        new WorkshopRouteDecision.Tools(
            List.of("HANDOUT", "PRACTICE"), List.of(), List.of()),
        new WorkshopRouteDecision.Policies(8, 2, 4, 3, 3, 1, true, true, false, true),
        new WorkshopRouteDecision.Output("WorkshopCompoundResult", "compound", true),
        new WorkshopRouteDecision.Fallback(
            "ASK_CLARIFICATION",
            "ASK_CLARIFICATION",
            "DISCLOSE_COMMON_KNOWLEDGE",
            "RETURN_PARTIAL_WITH_ERROR",
            "USE_DETERMINISTIC_FALLBACK",
            "SAFE_TEMPLATE"));
  }

  /** C3 决策：三条受权候选 + A* 选中项；候选集合与工具权限保持一致。 */
  public static WorkshopRouteDecision c3Decision(String routeId, String selectedCandidateId) {
    WorkshopRouteDecision base = multiDecision(routeId);
    var candidates =
        List.of(
            new WorkshopRouteDecision.SearchCandidate(
                "candidate_plan_first",
                "规划诊断优先",
                List.of("PathPlannerAgent", "HandoutAgent", "PracticeAgent"),
                List.of("PLAN", "HANDOUT", "PRACTICE"),
                3,
                2.66,
                0.1,
                0.0,
                2.76),
            new WorkshopRouteDecision.SearchCandidate(
                "candidate_rebuild_first",
                "知识重构优先",
                List.of("HandoutAgent", "PracticeAgent", "PathPlannerAgent"),
                List.of("HANDOUT", "PRACTICE", "PLAN"),
                3,
                2.63,
                0.15,
                0.15,
                2.93),
            new WorkshopRouteDecision.SearchCandidate(
                "candidate_practice_first",
                "练习探测优先",
                List.of("PracticeAgent", "HandoutAgent", "PathPlannerAgent"),
                List.of("PRACTICE", "HANDOUT", "PLAN"),
                3,
                2.62,
                0.15,
                0.75,
                3.52));
    return new WorkshopRouteDecision(
        base.routeId(),
        base.version(),
        new WorkshopRouteDecision.Task(
            "MULTI_TASK",
            "compound",
            List.of("HANDOUT", "PRACTICE", "PLAN"),
            "C3",
            0.94,
            false,
            ""),
        new WorkshopRouteDecision.Orchestration(
            "PLANNER_EXECUTOR", "PathPlannerAgent", List.of("HandoutAgent", "PracticeAgent")),
        new WorkshopRouteDecision.Tools(
            List.of("HANDOUT", "PRACTICE", "PLAN"), List.of(), List.of()),
        base.policies(),
        base.output(),
        base.fallback(),
        base.personalization(),
        new WorkshopRouteDecision.Search(
            "BOUNDED_TOT_ASTAR",
            candidates,
            selectedCandidateId,
            3,
            "total_cost=g_cost+h_cost+penalty",
            new WorkshopRouteDecision.ReplanPolicy(1)));
  }

  private static WorkshopRouteDecision withoutSearch(WorkshopRouteDecision decision) {
    return new WorkshopRouteDecision(
        decision.routeId(),
        decision.version(),
        decision.task(),
        decision.orchestration(),
        decision.tools(),
        decision.policies(),
        decision.output(),
        decision.fallback(),
        decision.personalization(),
        null);
  }
}
