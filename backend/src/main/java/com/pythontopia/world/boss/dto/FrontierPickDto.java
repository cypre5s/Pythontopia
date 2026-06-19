package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 知识前沿候选：一个待出题的 KC + 章节 + 建议难度 + 入选原因。 */
public record FrontierPickDto(
    @JsonProperty("kcCode") String kcCode,
    @JsonProperty("chapter") int chapter,
    @JsonProperty("difficulty") int difficulty,
    @JsonProperty("reason") String reason) {}
