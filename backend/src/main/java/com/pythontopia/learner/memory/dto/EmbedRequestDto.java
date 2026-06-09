package com.pythontopia.learner.memory.dto;

import java.util.List;

/** pythontopia-rag {@code POST /embed} 请求体。 */
public record EmbedRequestDto(List<String> texts) {}
