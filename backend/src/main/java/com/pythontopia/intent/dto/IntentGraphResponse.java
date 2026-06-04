package com.pythontopia.intent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Python graph 服务 {@code IntentRouteResponse} 的 snake_case 内部映射（仅供 {@link
 * com.pythontopia.intent.IntentGraphClient} 反序列化用），随后转成对前端的 camelCase {@link
 * IntentRouteResponse}。
 */
public record IntentGraphResponse(
    String intent,
    @JsonProperty("kc_codes") List<String> kcCodes,
    @JsonProperty("focus_hint") String focusHint,
    double confidence,
    String reason) {}
