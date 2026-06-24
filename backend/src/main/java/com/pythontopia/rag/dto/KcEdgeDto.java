package com.pythontopia.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GraphRAG 返回的可审计 KC 子图边。 */
public record KcEdgeDto(
    @JsonProperty("from_kc") String fromKc,
    @JsonProperty("to_kc") String toKc,
    String relation) {}
