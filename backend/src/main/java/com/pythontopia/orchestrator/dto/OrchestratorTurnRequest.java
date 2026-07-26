package com.pythontopia.orchestrator.dto;

import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.WorkshopRouteDecision;
import com.pythontopia.intent.LearnerRoutingContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 学习总管（OrchestratorAgent）单轮对话请求。
 *
 * <p>前端把当前学习上下文随每轮消息发来，Java 端只做透传代理：会话记忆与多智能体编排都在 Python
 * graph 服务里完成，后端不落库。除 {@code message} 外字段均可空；{@code sessionId} 为空时由后端
 * 生成一个临时会话 id。{@code courseKcCodes} 作为课程 KC 索引（{@code course_kc_index}）传给
 * Python 用于证据接地与防幻觉。
 */
public record OrchestratorTurnRequest(
    @NotBlank @Size(max = 4000) String message,
    @Size(max = 64) String sessionId,
    @Valid List<OrchestratorTurnMessage> history,
    Integer currentChapter,
    List<String> recentKcCodes,
    List<String> recentFailures,
    @Size(max = 2000) String profileBrief,
    List<String> courseKcCodes,
    @Valid WorkshopRouteDecision routeDecision,
    @Size(max = 1024) String routeExecutionToken,
    @Valid IntentRouteRequest.InteractionContext interaction,
    LearnerRoutingContext learnerContext) {

  public OrchestratorTurnRequest(
      String message,
      String sessionId,
      List<OrchestratorTurnMessage> history,
      Integer currentChapter,
      List<String> recentKcCodes,
      List<String> recentFailures,
      String profileBrief,
      List<String> courseKcCodes,
      WorkshopRouteDecision routeDecision,
      String routeExecutionToken,
      IntentRouteRequest.InteractionContext interaction) {
    this(
        message,
        sessionId,
        history,
        currentChapter,
        recentKcCodes,
        recentFailures,
        profileBrief,
        courseKcCodes,
        routeDecision,
        routeExecutionToken,
        interaction,
        null);
  }

  public OrchestratorTurnRequest(
      String message,
      String sessionId,
      List<OrchestratorTurnMessage> history,
      Integer currentChapter,
      List<String> recentKcCodes,
      List<String> recentFailures,
      String profileBrief,
      List<String> courseKcCodes) {
    this(
        message,
        sessionId,
        history,
        currentChapter,
        recentKcCodes,
        recentFailures,
        profileBrief,
        courseKcCodes,
        null,
        null,
        null,
        null);
  }

  public OrchestratorTurnRequest withInteraction(
      IntentRouteRequest.InteractionContext verifiedInteraction) {
    return new OrchestratorTurnRequest(
        message,
        sessionId,
        history,
        currentChapter,
        recentKcCodes,
        recentFailures,
        profileBrief,
        courseKcCodes,
        routeDecision,
        routeExecutionToken,
        verifiedInteraction,
        learnerContext);
  }

  public OrchestratorTurnRequest withCourseContext(
      List<String> verifiedRecentKcCodes, List<String> authoritativeCourseKcCodes) {
    return new OrchestratorTurnRequest(
        message,
        sessionId,
        history,
        currentChapter,
        verifiedRecentKcCodes,
        recentFailures,
        profileBrief,
        authoritativeCourseKcCodes,
        routeDecision,
        routeExecutionToken,
        interaction,
        learnerContext);
  }

  public OrchestratorTurnRequest withLearningContext(LearnerRoutingContext context) {
    LearnerRoutingContext safe = context == null ? LearnerRoutingContext.empty() : context;
    return new OrchestratorTurnRequest(
        message,
        sessionId,
        history,
        currentChapter,
        recentKcCodes,
        safe.strugglePatterns(),
        safe.profileBrief(),
        courseKcCodes,
        routeDecision,
        routeExecutionToken,
        interaction,
        safe);
  }
}
