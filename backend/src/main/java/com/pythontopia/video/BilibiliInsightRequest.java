package com.pythontopia.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 前端工坊「AI 讲解 / 看点」请求体：针对某 B 站视频（{@code bvid}）+ 学生问题（{@code question}）
 * + 相关 KC（{@code kcCodes}）求个性化概述与可点击跳转的关键时间点。
 *
 * <p>由 {@link BilibiliInsightService} 转发给 pythontopia-graph {@code POST
 * /agents/video/bilibili/insight}（graph 侧 snake_case {@code kc_codes}）。校验对齐 graph
 * {@code VideoInsightRequest}：{@code bvid} 必填且 ≤32，{@code question} ≤512，{@code kcCodes} 至多 10 项且每项 ≤32。
 */
public record BilibiliInsightRequest(
    @NotBlank @Size(max = 32) String bvid,
    @Size(max = 512) String question,
    @Size(max = 10) List<@Size(max = 32) String> kcCodes) {}
