package com.pythontopia.video;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一个关键时间点（可点击跳 {@code https://www.bilibili.com/video/{bvid}?t={tSec}}）。
 *
 * <p>同 {@link BilibiliVideoDto} 的「单 record 双职责」：既反序列化 graph 的 snake_case
 * （{@code t_sec}），又以前端期望的 camelCase（{@code tSec}）序列化输出；{@code label}/{@code source}
 * 两端同名，无需注解。{@code tSec} 只来自真实 outline / 分 P 时间轴（graph {@code timeline_grounding}
 * 已剔除幻觉时间点，后端不再二次校验）；{@code source∈{ai_conclusion,pages}}（审计用）。
 */
public record BilibiliKeyPointDto(
    @JsonProperty("tSec") @JsonAlias("t_sec") int tSec, String label, String source) {}
