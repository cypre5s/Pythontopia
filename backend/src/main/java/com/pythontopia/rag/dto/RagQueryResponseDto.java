package com.pythontopia.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * 课件 RAG 检索响应：{@code hits} 为证据命中列表（{@link #hits()} 对 null 兜底空列表）；
 * {@code no_evidence_found} 为 true 表示未检索到课件证据，调用方据此防幻觉、不得编造引用；
 * {@code rewritten_query} 为检索时改写后的查询。
 */
public record RagQueryResponseDto(
    List<RagHitDto> hits,
    @JsonProperty("no_evidence_found") boolean noEvidenceFound,
    @JsonProperty("rewritten_query") String rewrittenQuery,
    @JsonProperty("retrieval_mode") String retrievalMode,
    String route,
    List<KcEdgeDto> subgraph,
    @JsonProperty("graph_trace") Map<String, Object> graphTrace,
    @JsonProperty("video_trace") Map<String, Object> videoTrace) {

  public RagQueryResponseDto {
    if (hits == null) {
      hits = List.of();
    }
    if (subgraph == null) {
      subgraph = List.of();
    }
    if (graphTrace == null) {
      graphTrace = Map.of();
    }
    if (videoTrace == null) {
      videoTrace = Map.of();
    }
  }

  public RagQueryResponseDto(
      List<RagHitDto> hits, boolean noEvidenceFound, String rewrittenQuery) {
    this(hits, noEvidenceFound, rewrittenQuery, null, null, List.of(), Map.of(), Map.of());
  }
}
