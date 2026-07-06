package com.pythontopia.feature.endless

import kotlinx.serialization.Serializable

/** 无尽练习 DTO；复用课程题库与提交判题端点（无专用 endless 接口）。 */

@Serializable
data class EndlessKcDto(
    // 后端字段为 kcId 非 id，错名会导致反序列化失败、kcCodes() 静默返回空列表
    val kcId: Long = 0,
    val kcCode: String,
    val kcName: String = "",
)

@Serializable
data class EndlessProblemDto(
    val problemId: Long,
    val kcCode: String = "",
    val title: String = "",
    val statementMarkdown: String = "",
    val difficulty: String? = null,
) {
    val reward: Int
        get() = when (difficulty?.lowercase()) {
            "high", "hard" -> 30
            "medium", "mid" -> 20
            else -> 10
        }
}

@Serializable
data class EndlessSubmissionCreate(
    val problemId: Long,
    val code: String,
    val source: String = "TRAINING",
)

@Serializable
enum class EndlessStatus {
    PENDING, RUNNING, PASSED, FAILED, ERROR;

    val isTerminal: Boolean get() = this == PASSED || this == FAILED || this == ERROR
    val isSuccess: Boolean get() = this == PASSED

    val label: String
        get() = when (this) {
            PENDING -> "排队中"
            RUNNING -> "判题中"
            PASSED -> "通过"
            FAILED -> "答案错误"
            ERROR -> "运行错误"
        }
}

@Serializable
data class EndlessSubmissionDto(
    val id: Long,
    val problemId: Long = 0,
    val status: EndlessStatus,
    val runtimeMs: Int? = null,
    val passedCases: Int? = null,
    val totalCases: Int? = null,
    val traceback: String? = null,
) {
    val isTerminal: Boolean get() = status.isTerminal
}

@Serializable
data class EndlessJudgeResult(
    val verdict: String? = null,
    val passedCases: Int? = null,
    val totalCases: Int? = null,
    val failureReason: String? = null,
)

@Serializable
data class EndlessSubmissionDetail(
    val submission: EndlessSubmissionDto,
    val judgeResult: EndlessJudgeResult? = null,
) {
    fun merged(): EndlessSubmissionDto = submission.copy(
        passedCases = judgeResult?.passedCases ?: submission.passedCases,
        totalCases = judgeResult?.totalCases ?: submission.totalCases,
        traceback = judgeResult?.failureReason ?: submission.traceback,
    )
}
