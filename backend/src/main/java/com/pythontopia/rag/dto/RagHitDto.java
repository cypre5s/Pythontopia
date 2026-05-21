package com.pythontopia.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RagHitDto(
    @JsonProperty("chunk_id") String chunkId,
    @JsonProperty("page_id") long pageId,
    @JsonProperty("page_no") int pageNo,
    @JsonProperty("chapter_id") Long chapterId,
    @JsonProperty("kc_codes") List<String> kcCodes,
    String text,
    double score) {}
