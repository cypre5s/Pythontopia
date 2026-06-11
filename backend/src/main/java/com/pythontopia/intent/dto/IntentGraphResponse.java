package com.pythontopia.intent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Python graph 服务 {@code IntentRouteResponse} 的 snake_case 内部映射（仅供 {@link
 * com.pythontopia.intent.IntentGraphClient} 反序列化用），随后转成对前端的 camelCase {@link
 * IntentRouteResponse}。
 *
 * <p>R-16 新增：{@code target}（NAVIGATE 跳转地标，非 NAVIGATE 为空串）、{@code secondary_intent}
 * （组合意图次级动作，无则 null）。
 */
public record IntentGraphResponse(
    String intent,
    @JsonProperty("kc_codes") List<String> kcCodes,
    @JsonProperty("focus_hint") String focusHint,
    double confidence,
    String reason,
    String target,
    @JsonProperty("secondary_intent") String secondaryIntent) {}
