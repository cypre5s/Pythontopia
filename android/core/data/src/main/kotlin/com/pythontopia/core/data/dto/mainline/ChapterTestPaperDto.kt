package com.pythontopia.core.data.dto.mainline

import kotlinx.serialization.Serializable

/**
 * 开考下发的**去答案**章测题面（`POST /api/mainline/chapters/{chapter}/test/start`）。
 * 不含答案/解析/隐藏用例——解析仅在提交后由 [ChapterTestResultDto] 返回。`type` ∈ CHOICE/JUDGE/FILL/CODING。
 */
@Serializable
data class ChapterTestPaperDto(
    val testId: Long,
    val chapter: Int = 0,
    val attemptNo: Int = 0,
    val status: String? = null,
    val timeLimitSec: Int = 0,
    val deadlineAt: String? = null,
    val items: List<ChapterTestQuestionViewDto> = emptyList(),
)

@Serializable
data class ChapterTestQuestionViewDto(
    val qid: String,
    val type: String? = null,
    val score: Int = 0,
    val stem: String? = null,
    val title: String? = null,
    val statementMarkdown: String? = null,
    val options: List<String> = emptyList(),
    val samples: List<ChapterTestSampleViewDto> = emptyList(),
)

@Serializable
data class ChapterTestSampleViewDto(val input: String? = null, val output: String? = null)
