package com.pythontopia.courseware.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pythontopia.rag.dto.KcEdgeDto;
import java.util.List;
import java.util.Map;

/** 课件问答响应：LLM 作答 Markdown + 可跳转页码引用；无证据时 noEvidence=true。 */
public record CoursewareAnswerResponseDto(
    String answerMd,
    List<CoursewareCitationDto> citations,
    boolean noEvidence,
    String route,
    @JsonProperty("retrieval_mode") String retrievalMode,
    List<KcEdgeDto> subgraph,
    @JsonProperty("graph_trace") Map<String, Object> graphTrace) {

  public CoursewareAnswerResponseDto {
    if (citations == null) {
      citations = List.of();
    }
    if (subgraph == null) {
      subgraph = List.of();
    }
    if (graphTrace == null) {
      graphTrace = Map.of();
    }
  }

  public CoursewareAnswerResponseDto(
      String answerMd, List<CoursewareCitationDto> citations, boolean noEvidence) {
    this(answerMd, citations, noEvidence, null, null, List.of(), Map.of());
  }
}
