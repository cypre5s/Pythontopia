package com.pythontopia.core.data.dto.mainline

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * R-15 主线冒险地图总览，对应后端 `MainlineOverviewDto`（`GET /api/mainline/overview`）。
 * 客户端只读：渲染章节/小节进度，星级公式与解锁判定全在后端。
 */
@Serializable
data class MainlineOverviewDto(
    val totalStars: Int = 0,
    val chapters: List<ChapterOverviewDto> = emptyList(),
)

@Serializable
data class ChapterOverviewDto(
    val chapter: Int,
    val title: String? = null,
    val unlocked: Boolean = false,
    val stars: Int = 0,
    val bestTestScore: Int? = null,
    val stages: List<StageOverviewDto> = emptyList(),
    val testStatus: String? = null,
    val starBreakdown: JsonElement? = null,
    val recovery: ChapterRecoveryDto? = null,
)

@Serializable
data class StageOverviewDto(
    val id: Long,
    val index: Int = 0,
    val title: String? = null,
    val videoWatchedSec: Int = 0,
    val requiredVideoSec: Int = 0,
    val pagesRead: Int = 0,
    val requiredPages: Int = 0,
    val examplesDone: Int = 0,
    val requiredExamples: Int = 0,
    val completed: Boolean = false,
)

/** 章节补星进度（错题复盘 / 无尽挑战途径）。 */
@Serializable
data class ChapterRecoveryDto(
    val errorbookDone: Boolean = false,
    val endlessDone: Boolean = false,
)
