package com.pythontopia.core.data.dto.mainline

import kotlinx.serialization.Serializable

/** 「看」打点：可见时长增量（秒，须 ≥1；服务端钳制单次上限并限频防挂机）。 */
@Serializable
data class VideoProgressRequest(val deltaSec: Int)

/** 「学」单页阅读打点（幂等）。 */
@Serializable
data class PageReadRequest(val pageId: Long)

/** 「学」批量阅读打点（阅读器按批上报，服务端并集去重幂等累加）。 */
@Serializable
data class PagesReadRequest(val pageIds: List<Long>)

/** 「练」例题完成打点；带 submissionId 时服务端校验本人且 PASSED。 */
@Serializable
data class ExampleDoneRequest(val problemId: Long, val submissionId: Long? = null)

/** 无尽一轮小结上报 → ENDLESS_RECOVERY 补星判定。 */
@Serializable
data class EndlessSummaryRequest(
    val chapter: Int,
    val correct: Int,
    val total: Int,
    val streak: Int,
)

/** 补星端点返回：本次是否真正发放补星（已发放/未达标/已封顶均 false）。 */
@Serializable
data class StarAwardResultDto(val starAwarded: Boolean = false)

/** 章末测试预生成状态。 */
@Serializable
data class TestPrepareResponse(val status: String? = null)

/** 「练」客观题作答提交（CHOICE/JUDGE 长度 1；FILL 按空位顺序）。 */
@Serializable
data class PracticeAnswerRequest(val answers: List<String>)

/** 「练」客观题判分结果（作答后回传正确答案 + 讲解，供自检，不提前泄题）。 */
@Serializable
data class PracticeAnswerResultDto(
    val questionId: Long,
    val correct: Boolean = false,
    val correctAnswers: List<String> = emptyList(),
    val explanation: String? = null,
)
