package com.pythontopia.video;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 「B站精选讲解」检索代理：转调 pythontopia-graph 的 {@code GET /agents/video/bilibili/search}
 * （取数库 {@code bilibili-api-python} 是 GPL，隔离在 Python 微服务，后端只做 HTTP 代理）。
 *
 * <p>镜像 {@link com.pythontopia.workshop.graph.WorkshopGraphClient} 的 {@code graphRestTemplate}
 * + {@link GraphServiceProperties} 用法。graph 返回 {@code {"items":[...]}}，本类解包成
 * {@code List<BilibiliVideoDto>}；空结果原样返回空列表（前端降级占位，不报错），仅在 HTTP/风控
 * 失败时抛 {@link BusinessException}（{@code VIDEO_SEARCH_UNAVAILABLE}），由 {@code GlobalExceptionHandler}
 * 转标准 {@code ApiError}。后端到 graph 的内部调用统一附带 {@code X-Internal-Token}。
 */
@Service
public class BilibiliVideoService {

  private static final Logger LOG = LoggerFactory.getLogger(BilibiliVideoService.class);

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public BilibiliVideoService(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public List<BilibiliVideoDto> search(String keyword, int limit) {
    URI uri =
        UriComponentsBuilder.fromHttpUrl(properties.graphUrl())
            .path("/agents/video/bilibili/search")
            .queryParam("keyword", keyword)
            .queryParam("limit", limit)
            .encode()
            .build()
            .toUri();
    HttpHeaders headers = new HttpHeaders();
    GraphInternalTokenSupport.apply(headers, properties);
    try {
      GraphSearchResponse response =
          restTemplate
              .exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), GraphSearchResponse.class)
              .getBody();
      if (response == null || response.items() == null) {
        return List.of();
      }
      return response.items();
    } catch (RestClientException exception) {
      LOG.warn("bilibili search via graph failed: {}", exception.getClass().getSimpleName());
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "VIDEO_SEARCH_UNAVAILABLE", "B站视频检索服务不可用");
    }
  }

  /** graph 响应外壳：{@code {"items":[...]}}。 */
  record GraphSearchResponse(List<BilibiliVideoDto> items) {}
}
