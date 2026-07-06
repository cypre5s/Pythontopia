package com.pythontopia.video;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 下发给前端 {@code src/api/bilibiliVideo.ts} 的「B站精选讲解」外部视频卡片（源无关，仅展示
 * 封面缩略图 + 跳转链接，不进课件 grounding、不下载视频）。
 *
 * <p>同一个 record 既要**反序列化**上游 pythontopia-graph 返回的 snake_case
 * （{@code "cover"} / {@code "duration_sec"}），又要以前端期望的 camelCase
 * （{@code coverUrl} / {@code durationSec}）**序列化输出**。因此对这两个发散字段用
 * {@link JsonProperty} 固定输出名为 camelCase，并用 {@link JsonAlias} 额外接受 graph 的
 * snake_case 输入；其余字段两端同名（{@code bvid/title/author/description/play/danmaku/pubdate/url}），
 * 无需注解。
 *
 * <p>{@code description} 是 UP 主填写的视频简介（graph 已去标签/反转义/截断），仅作为生成
 * {@code aiSummary} 的输入素材透传，前端不再直接展示。{@code aiSummary}（graph 的
 * {@code ai_summary}）是 graph 按 标题+简介+时长 让 LLM 概括的一句话「这视频讲了啥」，
 * LLM 不可用/超时时为空串（前端给中性占位并标注 AI，不伪造内容）。
 */
public record BilibiliVideoDto(
    String bvid,
    String title,
    String author,
    String description,
    @JsonProperty("aiSummary") @JsonAlias("ai_summary") String aiSummary,
    @JsonProperty("coverUrl") @JsonAlias("cover") String coverUrl,
    @JsonProperty("durationSec") @JsonAlias("duration_sec") int durationSec,
    long play,
    long danmaku,
    long pubdate,
    String url) {}
