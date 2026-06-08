package com.pythontopia.workshop.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.GraphSseConsumer;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.GraphClientRetryHelper;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class WorkshopGraphClient {

  private static final Logger log = LoggerFactory.getLogger(WorkshopGraphClient.class);
  private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;
  private final ObjectMapper objectMapper;
  private final SseRelayExecutor sseRelayExecutor;

  public WorkshopGraphClient(
      RestTemplate graphRestTemplate,
      GraphServiceProperties properties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.sseRelayExecutor = sseRelayExecutor;
  }

  public ResourceProduceResponseDto produce(ResourceProduceRequestDto request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    setInternalToken(headers);
    return GraphClientRetryHelper.withRetry(
        "workshop",
        () ->
            restTemplate
                .exchange(
                    properties.graphUrl() + "/agents/resource/produce",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    ResourceProduceResponseDto.class)
                .getBody());
  }

  private void setInternalToken(HttpHeaders headers) {
    GraphInternalTokenSupport.apply(headers, properties);
  }

  /** 从 graph 服务流式 produce 端点转发 SSE：事件经 emitter 透传，onResult 回调处理最终 result 事件。 */
  public void produceStream(
      ResourceProduceRequestDto request,
      SseEmitter emitter,
      Function<String, String> onResult) {
    String url = properties.graphUrl() + "/agents/resource/produce/stream";
    sseRelayExecutor.submit(() -> {
      try {
        GraphSseConsumer.consume(
            url,
            request,
            objectMapper,
            GraphInternalTokenSupport.tokenOrNull(properties),
            (event, data) -> {
          try {
            String outboundData = data;
            if ("result".equals(event)) {
              outboundData = onResult.apply(data);
            }
            emitter.send(SseEmitter.event().name(event).data(outboundData));
          } catch (IOException e) {
            log.debug("SSE emitter write failed, client likely disconnected", e);
          } catch (Exception e) {
            log.warn("Failed to process Graph SSE result", e);
            sendError(emitter, "RESOURCE_STREAM_RESULT_FAILED", "资源生成结果保存失败");
          }
        });
        emitter.complete();
      } catch (IOException e) {
        log.warn("Graph SSE stream failed, falling back to non-streaming", e);
        try {
          ResourceProduceResponseDto fallback = produce(request);
          String fallbackJson = objectMapper.writeValueAsString(fallback);
          String outboundJson = onResult.apply(fallbackJson);
          emitter.send(SseEmitter.event().name("result").data(outboundJson));
          emitter.complete();
        } catch (Exception fallbackEx) {
          log.error("SSE fallback also failed", fallbackEx);
          sendError(emitter, "RESOURCE_STREAM_FALLBACK_FAILED", "资源生成服务暂不可用");
          emitter.completeWithError(fallbackEx);
        }
      }
    });
  }

  private void sendError(SseEmitter emitter, String code, String message) {
    try {
      String payload = objectMapper.writeValueAsString(Map.of("code", code, "message", message));
      emitter.send(SseEmitter.event().name("error").data(payload));
    } catch (Exception sendFailure) {
      log.debug("Failed to send SSE error event", sendFailure);
    }
  }
}
