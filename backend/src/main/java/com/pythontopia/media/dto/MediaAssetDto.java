package com.pythontopia.media.dto;

import com.pythontopia.media.MediaAssetUrls;
import com.pythontopia.media.domain.MediaAsset;
import java.util.List;

/**
 * 下发给前端的多模态媒资元数据。{@code url} 指向带签名的 {@code GET /api/media/assets/{id}?k=<sig>}，
 * 可直接用于 {@code <img src>} / {@code <video src>}；客户端应原样使用该 url，不要自行按 id 拼接
 * （无签名的裸 id 直链会被服务端 404，见 {@link MediaAssetUrls}）。{@code degraded=true} 时前端应
 * 显示产物不完整提示；文生图缺少真实 Provider 时不会返回该 DTO。
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

  public static MediaAssetDto from(MediaAsset asset, MediaAssetUrls assetUrls) {
    return new MediaAssetDto(
        asset.id(),
        asset.kind().name(),
        assetUrls.urlFor(asset.id()),
        asset.mimeType(),
        asset.degraded(),
        asset.source(),
        asset.prompt(),
        asset.kcCodes());
  }
}
