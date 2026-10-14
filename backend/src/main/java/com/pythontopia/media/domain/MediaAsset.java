package com.pythontopia.media.domain;

import com.pythontopia.media.MediaAssetKind;
import java.time.Instant;
import java.util.List;

/**
 * 一条多模态媒资记录（对应 {@code media_asset} 表）。{@code filePath} 是相对生成目录的文件名，
 * 实际字节落在 {@code pythontopia.media.generatedDir} 下（不入 git，可重生）。
 *
 * @param degraded true 表示服务明确将产物标记为降级；关闭可选 TTS 不构成降级。
 * @param source 资产来源：{@code XFYUN} / {@code FFMPEG}；旧数据可能仍含 {@code PLACEHOLDER}。
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
