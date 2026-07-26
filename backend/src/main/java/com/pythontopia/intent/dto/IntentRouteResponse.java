package com.pythontopia.intent.dto;

import java.util.List;

/**
 * 百晓工坊「意图路由」响应（对前端 camelCase）。
 *
 * <p>{@code intent} 取值即 graph 的 IntentKind 17 值：ASK / VIDEO / REVIEW / BROWSE / HANDOUT / MINDMAP /
 * PRACTICE / CODE_LAB / READING / STORYBOARD / ASKME / EXPLAIN_CODE / FIX_BUG / PLAN / PROGRESS /
 * NAVIGATE / MY_STUDY。前端据此分派到导师 / 工坊 / 总管 SSE 或纯前端动作。{@code kcCodes} 为建议
 * 知识点（手动 @ 优先），{@code focusHint} 为一句话学习重点。Java 仅透传，{@code intent} 是
 * String，无需随枚举改动。
 *
 * <p>R-16 新增：{@code target}（仅 NAVIGATE 给出跳转地标 library/arena/bugmine/world/workshop，
 * 其余意图为空串）、{@code secondaryIntent}（一句话含两个诉求时的次级意图，无则 null——前端在主
 * 动作完成后出「接着…」一键 chip）。
 */
public record IntentRouteResponse(
    String intent,
    List<String> kcCodes,
    String focusHint,
    double confidence,
    String reason,
    String target,
    String secondaryIntent,
    String namespace,
    List<String> candidateIntents,
    boolean needsClarification,
    String clarificationPrompt,
    String riskClass,
    String renderMode,
    String reasonCode,
    WorkshopRouteDecision routeDecision,
    String routeExecutionToken) {

  public IntentRouteResponse(
      String intent,
      List<String> kcCodes,
      String focusHint,
      double confidence,
      String reason,
      String target,
      String secondaryIntent,
      String namespace,
      List<String> candidateIntents,
      boolean needsClarification,
      String clarificationPrompt,
      String riskClass,
      String renderMode,
      String reasonCode,
      WorkshopRouteDecision routeDecision) {
    this(
        intent,
        kcCodes,
        focusHint,
        confidence,
        reason,
        target,
        secondaryIntent,
        namespace,
        candidateIntents,
        needsClarification,
        clarificationPrompt,
        riskClass,
        renderMode,
        reasonCode,
        routeDecision,
        null);
  }

  public IntentRouteResponse withRouteExecutionToken(String token) {
    return new IntentRouteResponse(
        intent,
        kcCodes,
        focusHint,
        confidence,
        reason,
        target,
        secondaryIntent,
        namespace,
        candidateIntents,
        needsClarification,
        clarificationPrompt,
        riskClass,
        renderMode,
        reasonCode,
        routeDecision,
        token);
  }
}
