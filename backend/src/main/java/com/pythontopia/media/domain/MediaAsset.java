package com.pythontopia.media.domain;

import com.pythontopia.media.MediaAssetKind;
import java.time.Instant;
import java.util.List;

/**
 * 一条多模态媒资记录（对应 {@code media_asset} 表）。{@code filePath} 是相对生成目录的文件名，
 * 实际字节落在 {@code pythontopia.media.generatedDir} 下（不入 git，可重生）。
 *
 * @param degraded true 表示本地占位资产（未配置讯飞 apikey 或上游失败的降级产物）。
 * @param source 资产来源：{@code XFYUN} / {@code PLACEHOLDER} / {@code FFMPEG}。
 */
public record MediaAsset(
    Long id,
    Long userId,
    MediaAssetKind kind,
    String prompt,
    String filePath,
    String mimeType,
    boolean degraded,
    String source,
    List<String> kcCodes,
    Instant createdAt) {}
