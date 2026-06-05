package com.pythontopia.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 课件 RAG 检索命中的单条证据：定位到具体课件页（{@code page_id} / {@code page_no}）及其所属
 * 章节、知识点（{@code kc_codes}），{@code score} 为相关度；字段 snake_case 对齐 pythontopia-rag。
 */
public record RagHitDto(
    @JsonProperty("chunk_id") String chunkId,
    @JsonProperty("page_id") long pageId,
    @JsonProperty("page_no") int pageNo,
    @JsonProperty("chapter_id") Long chapterId,
    @JsonProperty("kc_codes") List<String> kcCodes,
    String text,
    double score) {}
