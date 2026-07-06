package com.pythontopia.workshop.upload.retrieval.dto;

import java.util.List;

/** {@code POST /api/workshop/uploads/retrieve} 响应体：按相关性降序的命中列表。 */
public record RetrieveResponse(List<RetrievalHit> hits) {}
