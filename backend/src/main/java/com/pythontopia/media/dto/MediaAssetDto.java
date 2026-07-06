package com.pythontopia.media.dto;

import com.pythontopia.media.domain.MediaAsset;
import java.util.List;

/**
 * 下发给前端的多模态媒资元数据。{@code url} 指向 {@code GET /api/media/assets/{id}}，可直接用于
 * {@code <img src>} / {@code <video src>}；{@code degraded=true} 时前端可显示「占位/未接入真实模型」角标。
 */
public record MediaAssetDto(
    long id,
    String kind,
    String url,
    String mimeType,
    boolean degraded,
    String source,
    String prompt,
    List<String> kcCodes) {

  public static MediaAssetDto from(MediaAsset asset) {
    return new MediaAssetDto(
        asset.id(),
        asset.kind().name(),
        "/api/media/assets/" + asset.id(),
        asset.mimeType(),
        asset.degraded(),
        asset.source(),
        asset.prompt(),
        asset.kcCodes());
  }
}
