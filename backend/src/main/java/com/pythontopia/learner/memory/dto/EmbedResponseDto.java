package com.pythontopia.learner.memory.dto;

import java.util.List;

/**
 * pythontopia-rag {@code POST /embed} 响应体：{@code vectors} 与请求 {@code texts} 顺序一一对应；
 * {@code model}/{@code dim} 为当前 embedder 元信息（hash/bge/... 随 PYT_RAG_EMBEDDER 切换）。
 */
public record EmbedResponseDto(String model, int dim, List<List<Double>> vectors) {}
