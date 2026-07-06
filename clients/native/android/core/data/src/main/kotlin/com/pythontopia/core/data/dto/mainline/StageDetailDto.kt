package com.pythontopia.core.data.dto.mainline

import kotlinx.serialization.Serializable

/**
 * 主线小节学习页详情（`GET /api/mainline/stages/{stageId}`）：三件套（看/学/练）静态定义 +
 * 当前用户已完成 id 集合。对应后端 `StageDetailDto`。
 */
@Serializable
data class StageDetailDto(
    val id: Long,
    val chapter: Int = 0,
    val chapterTitle: String? = null,
    val index: Int = 0,
    val title: String? = null,
    val kcCodes: List<String> = emptyList(),
    val videoKeyword: String? = null,
    val coursewarePageIds: List<Long> = emptyList(),
    val exampleProblemIds: List<Long> = emptyList(),
    val requiredVideoSec: Int = 0,
    val requiredPages: Int = 0,
    val requiredExamples: Int = 0,
    val requiredQuestions: Int = 0,
    val videos: List<StageVideoDto> = emptyList(),
    val examples: List<StageExampleDto> = emptyList(),
    val questions: List<StagePracticeQuestionDto> = emptyList(),
    val videoWatchedSec: Int = 0,
    val pagesReadIds: List<Long> = emptyList(),
    val examplesDoneIds: List<Long> = emptyList(),
    val questionsCorrectIds: List<Long> = emptyList(),
    val completed: Boolean = false,
)

/** 「看」内置课程视频条目；字节流由 `GET /api/course/videos/{id}` 提供（支持 Range）。 */
@Serializable
data class StageVideoDto(val id: Long, val title: String? = null, val durationSec: Int = 0)

/** 「练」OJ 编程题展示条目；`id` 为 course_problem.id，训练场真实判题后 example-done 打点。 */
@Serializable
data class StageExampleDto(
    val id: Long,
    val displayNo: String? = null,
    val displayName: String? = null,
    val kcCode: String? = null,
)

/** 「练」客观题题面（不含答案，防泄题；判分走 practice/answer 端点）。 */
@Serializable
data class StagePracticeQuestionDto(
    val id: Long,
    val kcCode: String? = null,
    val type: PracticeQuestionType? = null,
    val ordinal: Int = 0,
    val stem: String? = null,
    val options: List<String> = emptyList(),
    val blankCount: Int = 0,
)

@Serializable
enum class PracticeQuestionType { CHOICE, JUDGE, FILL }
