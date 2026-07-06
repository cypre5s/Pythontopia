package com.pythontopia.mainline.dto;

/** stage 三件套打点的统一响应（video-progress / page-read / example-done 共用）。 */
public record StageProgressDto(
    long stageId,
    int chapter,
    int stageIndex,
    int videoWatchedSec,
    int requiredVideoSec,
    boolean videoComplete,
    int pagesRead,
    int requiredPages,
    boolean pagesComplete,
    int examplesDone,
    int requiredExamples,
    boolean examplesComplete,
    boolean completed) {}
