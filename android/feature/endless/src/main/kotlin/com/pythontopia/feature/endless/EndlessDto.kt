package com.pythontopia.feature.endless

import kotlinx.serialization.Serializable

/**
 * 无尽练习 DTO。
 *
 * 复用既有课程题库 + 提交判题端点（无专用 endless 接口）：
 *  - GET  /api/course/kcs               → [EndlessKcDto]（KC 池）
 *  - GET  /api/course/problems?kcCode=  → [EndlessProblemDto]
 *  - POST /api/submissions              → [EndlessSubmissionDto]（source=TRAINING）
 *  - GET  /api/submissions/{id}         → [EndlessSubmissionDetail]（轮询直到终态）
 */

@Serializable
data class EndlessKcDto(
    // 后端 KcDto 序列化字段为 kcId（默认 Jackson camelCase），不是 id；
    // 之前用 `id` 会因缺字段反序列化失败，导致 kcCodes() 静默兜底成空列表。
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
