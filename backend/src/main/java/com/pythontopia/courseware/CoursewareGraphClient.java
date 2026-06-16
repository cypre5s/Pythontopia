package com.pythontopia.courseware;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import com.pythontopia.courseware.dto.CoursewareAnswerRequestDto;
import com.pythontopia.courseware.dto.CoursewareAnswerResponseDto;
import com.pythontopia.courseware.dto.CoursewareCitationDto;
import com.pythontopia.rag.RagUnavailableException;
import java.util.List;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 课件问答（LLM 作答 + 页码引用）的 graph 客户端：POST graph 的 {@code /agents/courseware/answer}；
 * 服务不可达时抛 {@link RagUnavailableException}（HTTP 502），前端据此回退本地片段检索。
 */
@Component
public class CoursewareGraphClient {

  private static final int DEFAULT_TOP_K = 6;

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public CoursewareGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public CoursewareAnswerResponseDto answer(CoursewareAnswerRequestDto request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    GraphInternalTokenSupport.apply(headers, properties);
    GraphRequest payload =
        new GraphRequest(
            request.query(),
            request.kcCodes() == null ? List.of() : request.kcCodes(),
            request.topK() == null ? DEFAULT_TOP_K : request.topK());
    HttpEntity<GraphRequest> entity = new HttpEntity<>(payload, headers);
    try {
      GraphResponse body =
          restTemplate
              .exchange(
                  properties.graphUrl() + "/agents/courseware/answer",
                  HttpMethod.POST,
                  entity,
                  GraphResponse.class)
              .getBody();
      if (body == null) {
        throw new RagUnavailableException("课件问答服务返回空响应", null);
      }
      return body.toDto();
    } catch (RestClientException ex) {
      throw new RagUnavailableException("课件问答服务暂不可用", ex);
    }
  }

  /** 请求体：对齐 graph routes.courseware.CoursewareAnswerInput（snake_case）。 */
  record GraphRequest(
      @JsonProperty("query") String query,
      @JsonProperty("kc_codes") List<String> kcCodes,
      @JsonProperty("top_k") int topK) {}

  /** 响应体：对齐 graph routes.courseware.CoursewareAnswerOutput（snake_case）。 */
  record GraphResponse(
      @JsonProperty("answer_md") String answerMd,
      @JsonProperty("citations") List<GraphCitation> citations,
      @JsonProperty("no_evidence") boolean noEvidence) {
    CoursewareAnswerResponseDto toDto() {
      List<CoursewareCitationDto> mapped =
          (citations == null ? List.<GraphCitation>of() : citations)
              .stream().map(GraphCitation::toDto).toList();
      return new CoursewareAnswerResponseDto(answerMd == null ? "" : answerMd, mapped, noEvidence);
    }
  }

  /** 引用项：对齐 graph CoursewareCitation（snake_case）。 */
  record GraphCitation(
      @JsonProperty("page_id") int pageId,
      @JsonProperty("page_no") int pageNo,
      @JsonProperty("chapter_id") Integer chapterId,
      @JsonProperty("quote") String quote) {
    CoursewareCitationDto toDto() {
      return new CoursewareCitationDto(pageId, pageNo, chapterId, quote == null ? "" : quote);
    }
  }
}
