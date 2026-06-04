package com.pythontopia.intent.dto;

import java.util.List;

/**
 * 百晓工坊「意图路由」响应（对前端 camelCase）。
 *
 * <p>{@code intent} 取值：ASK / HANDOUT / MINDMAP / PRACTICE / CODE_LAB / READING / STORYBOARD。
 * 前端据此把请求分派到既有的导师（{@code /api/tutor/ask}）或工坊（{@code /api/workshop/resources/stream}）
 * SSE 管线；{@code kcCodes} 为建议知识点（手动 @ 优先），{@code focusHint} 为一句话学习重点。
 */
public record IntentRouteResponse(
    String intent, List<String> kcCodes, String focusHint, double confidence, String reason) {}
