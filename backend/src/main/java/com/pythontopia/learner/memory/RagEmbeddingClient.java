package com.pythontopia.learner.memory;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.learner.memory.dto.EmbedRequestDto;
import com.pythontopia.learner.memory.dto.EmbedResponseDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * {@link EmbeddingClient} 的 HTTP 实现：POST 到 pythontopia-rag 的 {@code /embed}，走<strong>独立</strong>的
 * {@code ragRestTemplate}（已配 connect/read 超时，下游不可达时阈值内 fail-fast）。与 graph 调用物理隔离，
 * 避免 {@code @Async} embedding 旁路被绑定 graph 模板的集成测试 mock 拦截成「未预期请求」。
 *
 * <p>所有异常路径（服务不可达、超时、响应缺向量）一律降级为 empty 并 {@code log.warn}，绝不向调用方抛出，
 * 确保记忆 embedding 写入失败不影响学习事件主链路。
 */
@Component
public class RagEmbeddingClient implements EmbeddingClient {

  private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
  private static final Logger log = LoggerFactory.getLogger(RagEmbeddingClient.class);

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public RagEmbeddingClient(RestTemplate ragRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = ragRestTemplate;
    this.properties = properties;
  }

  @Override
  public Optional<float[]> embed(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    List<Optional<float[]>> result = embedBatch(List.of(text));
    return result.isEmpty() ? Optional.empty() : result.get(0);
  }

  @Override
  public List<Optional<float[]>> embedBatch(List<String> texts) {
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String token = properties.ragAdminToken();
    if (token != null && !token.isBlank()) {
      headers.set(INTERNAL_TOKEN_HEADER, token);
    }
    HttpEntity<EmbedRequestDto> entity = new HttpEntity<>(new EmbedRequestDto(texts), headers);
    try {
      EmbedResponseDto body =
          restTemplate
              .exchange(
                  properties.ragUrl() + "/embed", HttpMethod.POST, entity, EmbedResponseDto.class)
              .getBody();
      return toVectors(texts.size(), body);
    } catch (RestClientException exception) {
      log.warn("embedding 服务不可用，记忆向量降级为空：{}", exception.getMessage());
      return emptyVectors(texts.size());
    }
  }

  private static List<Optional<float[]>> toVectors(int expected, EmbedResponseDto body) {
    if (body == null || body.vectors() == null) {
      return emptyVectors(expected);
    }
    if (body.model() != null && body.model().startsWith("hash-")) {
      return emptyVectors(expected);
    }
    List<Optional<float[]>> result = new ArrayList<>(expected);
    for (List<Double> vector : body.vectors()) {
      if (vector == null || vector.isEmpty()) {
        result.add(Optional.empty());
        continue;
      }
      float[] floats = new float[vector.size()];
      for (int i = 0; i < vector.size(); i++) {
        Double value = vector.get(i);
        floats[i] = value == null ? 0f : value.floatValue();
      }
      result.add(Optional.of(floats));
    }
    // 对齐入参长度：响应不足补 empty、多余截断（防御异常响应）。
    while (result.size() < expected) {
      result.add(Optional.empty());
    }
    return result.size() > expected ? List.copyOf(result.subList(0, expected)) : result;
  }

  private static List<Optional<float[]>> emptyVectors(int size) {
    List<Optional<float[]>> result = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      result.add(Optional.empty());
    }
    return result;
  }
}
