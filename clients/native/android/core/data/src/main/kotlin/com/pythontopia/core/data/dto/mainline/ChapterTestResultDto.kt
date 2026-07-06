package com.pythontopia.core.data.dto.mainline

import kotlinx.serialization.Serializable

/**
 * 章测结果（`POST .../submit` 与 `GET .../latest`）。仅终态（SUBMITTED/EXPIRED）回填逐题判分 +
 * 正确答案 + 解析；非终态 [items] 为空（不泄题）。
 */
@Serializable
data class ChapterTestResultDto(
    val testId: Long,
    val chapter: Int = 0,
    val attemptNo: Int = 0,
    val status: String? = null,
    val score: Int? = null,
    val maxScore: Int? = null,
    val passed: Boolean = false,
    val stars: Int = 0,
    val overtime: Boolean = false,
    val submittedAt: String? = null,
    val items: List<ChapterTestQuestionResultDto> = emptyList(),
    val integrity: ChapterTestIntegrityDto? = null,
)

@Serializable
data class ChapterTestQuestionResultDto(
    val qid: String,
    val type: String? = null,
    val correct: Boolean = false,
    val awardedScore: Int = 0,
    val maxScore: Int = 0,
    val yourAnswer: String? = null,
    val correctAnswer: String? = null,
    val explain: String? = null,
    val passedCases: Int? = null,
    val totalCases: Int? = null,
)

@Serializable
data class ChapterTestIntegrityDto(
    val pasteBlocked: Int = 0,
    val blurEvents: Int = 0,
    val overtime: Boolean = false,
)
