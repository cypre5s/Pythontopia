package com.pythontopia.core.data.dto.mainline

import kotlinx.serialization.Serializable

/** 三件套打点统一响应（video-progress / page-read / pages-read / example-done 共用）。 */
@Serializable
data class StageProgressDto(
    val stageId: Long,
    val chapter: Int = 0,
    val stageIndex: Int = 0,
    val videoWatchedSec: Int = 0,
    val requiredVideoSec: Int = 0,
    val videoComplete: Boolean = false,
    val pagesRead: Int = 0,
    val requiredPages: Int = 0,
    val pagesComplete: Boolean = false,
    val examplesDone: Int = 0,
    val requiredExamples: Int = 0,
    val examplesComplete: Boolean = false,
    val completed: Boolean = false,
)
