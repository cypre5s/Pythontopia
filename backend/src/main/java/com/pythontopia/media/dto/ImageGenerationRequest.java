package com.pythontopia.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 文生图请求。{@code prompt} 是图片描述；{@code label} 为兼容现有客户端保留（可空，一般传 KC code）；
 * {@code kcCodes} 仅用于审计/检索关联。
 */
public record ImageGenerationRequest(
    @NotBlank @Size(max = 600) String prompt,
    @Size(max = 48) String label,
    List<String> kcCodes) {

  public List<String> resolvedKcCodes() {
    return kcCodes == null ? List.of() : kcCodes;
  }
}
