package com.pythontopia.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 课件 RAG 检索响应：{@code hits} 为证据命中列表（{@link #hits()} 对 null 兜底空列表）；
 * {@code no_evidence_found} 为 true 表示未检索到课件证据，调用方据此防幻觉、不得编造引用；
 * {@code rewritten_query} 为检索时改写后的查询。
 */
public record RagQueryResponseDto(
    List<RagHitDto> hits,
    @JsonProperty("no_evidence_found") boolean noEvidenceFound,
    @JsonProperty("rewritten_query") String rewrittenQuery) {

  public List<RagHitDto> hits() {
    return hits == null ? List.of() : hits;
  }
}
