package com.pythontopia.mainline.dto;

/**
 * 小节「看」内置课程视频条目（来自 {@code video_manifest.json}，按章聚合）。
 *
 * <p>R-14 决策：每个小节的「看」展示<b>本章全部内置视频</b>，过线 = 累计观看本章总时长 × 80%
 * （{@code requiredVideoSec}，详见 {@link StageDetailDto}）。本 DTO 仅承载单条视频的展示信息，
 * 实际字节流由 {@code GET /api/course/videos/{id}} 提供（支持 Range，{@code <video src>} 直连）。
 */
public record StageVideoDto(long id, String title, int durationSec) {}
