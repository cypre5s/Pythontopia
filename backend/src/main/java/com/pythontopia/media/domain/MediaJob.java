package com.pythontopia.media.domain;

import com.pythontopia.media.MediaJobStatus;
import java.time.Instant;

/**
 * 一条媒体异步合成作业（对应 {@code media_job} 表，V158）。把"重、慢、受讯飞路数限制"的故事板短视频
 * 合成从 HTTP 请求线程解耦成可排队/可去重/可上报进度的后台作业。
 *
 * @param kind 作业类型，当前仅 {@code STORYBOARD_VIDEO}。
 * @param fingerprint 规格指纹（sha-256 hex，64 字符），同 {@code user+fingerprint} 已成功成片可幂等命中。
 * @param specJson 提交时的分镜规格快照（jsonb 原文）。
 * @param progressJson 实时进度 {@code {phase,sceneIndex,sceneTotal,roleKey,message,percent}}（jsonb 原文）。
 * @param assetId 合成成功后指向 {@code media_asset.id}；失败/进行中为空。
 * @param errorCode 失败码（如 {@code VIDEO_COMPOSE_UNAVAILABLE}）；成功为空。
 */
public record MediaJob(
    Long id,
    Long userId,
    String kind,
    MediaJobStatus status,
    String fingerprint,
    String specJson,
    String progressJson,
    Long assetId,
    String errorCode,
    Instant createdAt,
    Instant updatedAt) {}
