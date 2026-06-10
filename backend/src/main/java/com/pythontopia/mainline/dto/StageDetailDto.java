package com.pythontopia.mainline.dto;

import java.util.List;

/**
 * 小章节（stage）学习页详情（{@code GET /api/mainline/stages/{stageId}} 响应，r15 计划 §9 集成补充）。
 *
 * <p>地图 overview（{@link MainlineOverviewDto}）按 §11.4 保持轻量（仅进度计数），不携带 stage 静态定义；
 * 学习页（StageLearnPage）渲染三件套需要静态定义（videoKeyword / coursewarePageIds / exampleProblemIds /
 * kcCodes）+ 当前用户已完成 id 集合，故按需走本详情端点（A5↔A3 契约对账，方案 A）。
 *
 * <p>{@code requiredPages} / {@code requiredExamples} 为 seed 原值（0 表示「读完/做完全部」），由前端按
 * coursewarePageIds / exampleProblemIds 长度兜底展示。
 */
public record StageDetailDto(
    long id,
    int chapter,
    String chapterTitle,
    int index,
    String title,
    List<String> kcCodes,
    String videoKeyword,
    List<Long> coursewarePageIds,
    List<Long> exampleProblemIds,
    int requiredVideoSec,
    int requiredPages,
    int requiredExamples,
    int videoWatchedSec,
    List<Long> pagesReadIds,
    List<Long> examplesDoneIds,
    boolean completed) {}
