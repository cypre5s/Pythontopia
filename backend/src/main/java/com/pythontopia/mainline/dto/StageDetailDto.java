package com.pythontopia.mainline.dto;

import java.util.List;

/** stage 学习页详情（GET /api/mainline/stages/{stageId}，§9）：静态定义 + 当前用户已完成 id 集合。 */
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
