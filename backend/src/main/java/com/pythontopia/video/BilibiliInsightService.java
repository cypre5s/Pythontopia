package com.pythontopia.video;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 「B站视频概述 + 关键时间点」检索代理：转调 pythontopia-graph 的 {@code POST
 * /agents/video/bilibili/insight}（取数库 {@code bilibili-api-python} 是 GPL，隔离在 Python 微服务，
 * 后端只做 HTTP 代理）。
 *
 * <p>镜像 {@link BilibiliVideoService}：同一个 {@code graphRestTemplate} + {@link GraphServiceProperties}
 * + {@link BusinessException}（{@code VIDEO_INSIGHT_UNAVAILABLE}）错误处理，由 {@code
 * GlobalExceptionHandler} 转标准 {@code ApiError}。
 *
 * <p>与 search（GET）的唯一差异：insight 是 POST，需构造 JSON body，故按全栈 graph POST 客户端
 * （如 {@code WorkshopGraphClient}）的同款约定经共享 {@link GraphInternalTokenSupport} 附带
 * {@code X-Internal-Token}（token 未配置时不加头，dev/demo 下与 search 行为一致）。graph 直接返回
 * {@code VideoInsightResponse}（无外壳），本类反序列化为 camelCase 的 {@link BilibiliInsightResponse}。
 */
@Service
public class BilibiliInsightService {

  private static final Logger LOG = LoggerFactory.getLogger(BilibiliInsightService.class);

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public BilibiliInsightService(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public BilibiliInsightResponse insight(BilibiliInsightRequest request) {
    URI uri =
        UriComponentsBuilder.fromHttpUrl(properties.graphUrl())
            .path("/agents/video/bilibili/insight")
            .encode()
            .build()
            .toUri();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    GraphInternalTokenSupport.apply(headers, properties);

    // graph VideoInsightRequest 的 question / kc_codes 非 Optional（pydantic 默认 "" / []），
    // null 会被判型校验拒绝，这里补默认值；profile_snapshot 留给 graph 默认空画像（Track C 仅薄代理）。
    GraphInsightRequest payload =
        new GraphInsightRequest(
            request.bvid(),
            request.question() == null ? "" : request.question(),
            request.kcCodes() == null ? List.of() : request.kcCodes());

    try {
      BilibiliInsightResponse response =
          restTemplate
              .exchange(
                  uri,
                  HttpMethod.POST,
                  new HttpEntity<>(payload, headers),
                  BilibiliInsightResponse.class)
              .getBody();
      if (response == null) {
        throw new BusinessException(
            HttpStatus.BAD_GATEWAY, "VIDEO_INSIGHT_UNAVAILABLE", "B站视频概述服务不可用");
      }
      return response;
    } catch (RestClientException exception) {
      LOG.warn(
          "bilibili insight via graph failed errorType={}", exception.getClass().getSimpleName());
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "VIDEO_INSIGHT_UNAVAILABLE", "B站视频概述服务不可用");
    }
  }

  /**
   * graph {@code VideoInsightRequest} 入参外壳：把前端 camelCase {@code kcCodes} 序列化为 graph
   * 期望的 snake_case {@code kc_codes}；{@code bvid} / {@code question} 两端同名。
   */
  record GraphInsightRequest(
      String bvid, String question, @JsonProperty("kc_codes") List<String> kcCodes) {}
}
