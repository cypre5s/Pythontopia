package com.pythontopia.rag;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.rag.dto.RagQueryRequestDto;
import com.pythontopia.rag.dto.RagQueryResponseDto;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 课件 RAG 检索的 HTTP 客户端：POST 到 pythontopia-rag 的 {@code /rag/query}；
 * 服务不可达时抛 {@link RagUnavailableException}（HTTP 502）。
 */
@Component
public class RagClient {

  private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public RagClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public RagQueryResponseDto query(RagQueryRequestDto request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String ragAdminToken = properties.ragAdminToken();
    if (ragAdminToken != null && !ragAdminToken.isBlank()) {
      headers.set(INTERNAL_TOKEN_HEADER, ragAdminToken);
    }
    HttpEntity<RagQueryRequestDto> entity = new HttpEntity<>(request, headers);
    try {
      return restTemplate
          .exchange(
              properties.ragUrl() + "/rag/query",
              HttpMethod.POST,
              entity,
              RagQueryResponseDto.class)
          .getBody();
    } catch (RestClientException exception) {
      throw new RagUnavailableException("RAG 服务暂不可用", exception);
    }
  }
}
