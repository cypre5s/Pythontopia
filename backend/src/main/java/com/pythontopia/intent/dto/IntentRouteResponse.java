package com.pythontopia.intent.dto;

import java.util.List;

/**
 * 百晓工坊「意图路由」响应（对前端 camelCase）。
 *
 * <p>{@code intent} 取值：ASK / VIDEO / HANDOUT / MINDMAP / PRACTICE / CODE_LAB / READING /
 * STORYBOARD。前端据此分派：ASK→导师（{@code /api/tutor/ask}）；VIDEO→B站视频卡片
 * （{@code /api/videos/bilibili}，外部检索非生成）；6 类资源→工坊
 * （{@code /api/workshop/resources/stream}）SSE 管线。{@code kcCodes} 为建议知识点（手动 @ 优先），
 * {@code focusHint} 为一句话学习重点。Java 仅透传，{@code intent} 是 String，无需随枚举改动。
 */
public record IntentRouteResponse(
    String intent, List<String> kcCodes, String focusHint, double confidence, String reason) {}
