package com.pythontopia.video;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 下发给前端 {@code src/api/bilibiliVideo.ts} 的「视频概述 + 关键时间点」结果。
 *
 * <p>同 {@link BilibiliVideoDto} 的「单 record 双职责」：反序列化上游 pythontopia-graph
 * {@code VideoInsightResponse} 的 snake_case（{@code key_points} / {@code based_on}），又以前端期望的
 * camelCase（{@code keyPoints} / {@code basedOn}）序列化输出。因此对这两个发散字段用
 * {@link JsonProperty} 固定输出名为 camelCase，并用 {@link JsonAlias} 额外接受 graph 的 snake_case
 * 输入；{@code bvid} / {@code overview} / {@code degraded} 两端同名，无需注解。
 *
 * <p>{@code keyPoints} 为空表示拿不到真实时间轴（仅给 {@code overview}，绝不编造秒数）；
 * {@code basedOn∈{ai_conclusion,pages,metadata_only}} 标注概述依据；{@code degraded=true} 表示
 * best-effort 降级（LLM 不可用 / 取数失败），前端据此给中性占位、不伪造。
 */
public record BilibiliInsightResponse(
    String bvid,
    String overview,
    @JsonProperty("keyPoints") @JsonAlias("key_points") List<BilibiliKeyPointDto> keyPoints,
    @JsonProperty("basedOn") @JsonAlias("based_on") String basedOn,
    boolean degraded) {}
