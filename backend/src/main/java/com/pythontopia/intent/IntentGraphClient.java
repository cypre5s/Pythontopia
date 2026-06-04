package com.pythontopia.intent;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.intent.dto.IntentGraphResponse;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.IntentRouteResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 意图路由的非流式 graph 客户端，镜像 {@code CurriculumMapperClient}：把前端 camelCase 请求转成
 * Python 期望的 snake_case body，POST {@code /agents/intent/route}，再把 snake_case 响应映射回
 * camelCase {@link IntentRouteResponse}。KC 目录由前端透传（前端为 {@code @} 菜单本就全量加载），
 * 本客户端不查库；真正落库的工坊生成端会再次按课程 DB 校验 KC，grounding 完整性不受影响。
 */
@Component
public class IntentGraphClient {

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public IntentGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public IntentRouteResponse route(IntentRouteRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", request.message());
    if (request.manualKcCodes() != null && !request.manualKcCodes().isEmpty()) {
      body.put("manual_kc_codes", request.manualKcCodes());
    }
    List<Map<String, Object>> courseKcs = buildCourseKcs(request.courseKcs());
    if (!courseKcs.isEmpty()) {
      body.put("course_kcs", courseKcs);
    }
    if (request.currentChapter() != null) {
      body.put("current_chapter", request.currentChapter());
    }
    if (request.recentKcCodes() != null && !request.recentKcCodes().isEmpty()) {
      body.put("recent_kc_codes", request.recentKcCodes());
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

    IntentGraphResponse graph =
        restTemplate
            .exchange(
                properties.graphUrl() + "/agents/intent/route",
                HttpMethod.POST,
                entity,
                IntentGraphResponse.class)
            .getBody();

    if (graph == null || graph.intent() == null) {
      // graph 异常兜底：回落到答疑，避免前端拿到 null intent。
      return new IntentRouteResponse("ASK", List.of(), request.message(), 0.0, "graph 无响应");
    }
    return new IntentRouteResponse(
        graph.intent(),
        graph.kcCodes() != null ? graph.kcCodes() : List.of(),
        graph.focusHint() != null ? graph.focusHint() : "",
        graph.confidence(),
        graph.reason() != null ? graph.reason() : "");
  }

  private List<Map<String, Object>> buildCourseKcs(List<IntentRouteRequest.CourseKc> kcs) {
    List<Map<String, Object>> payload = new ArrayList<>();
    if (kcs == null) {
      return payload;
    }
    for (IntentRouteRequest.CourseKc kc : kcs) {
      if (kc == null || kc.code() == null || kc.code().isBlank()) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("code", kc.code());
      entry.put("name", kc.name() != null ? kc.name() : "");
      payload.add(entry);
    }
    return payload;
  }
}
