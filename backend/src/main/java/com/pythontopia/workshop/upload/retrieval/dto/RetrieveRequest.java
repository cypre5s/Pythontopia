package com.pythontopia.workshop.upload.retrieval.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code POST /api/workshop/uploads/retrieve} 请求体。
 *
 * @param query 检索语句（必填）。
 * @param uploadIds 可选：限定只在这些上传内检索；为空 / null 时检索本人全部上传。检索始终按登录态 user_id
 *     强制 scoped，uploadIds 仅为附加过滤。
 * @param topK 可选：期望命中条数；服务端 clamp 到合理区间，null 取默认。
 */
public record RetrieveRequest(
    @NotBlank @Size(max = 2000) String query,
    @Size(max = 100) List<Long> uploadIds,
    @Min(1) @Max(50) Integer topK) {}
