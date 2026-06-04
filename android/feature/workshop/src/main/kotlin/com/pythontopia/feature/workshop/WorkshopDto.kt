package com.pythontopia.feature.workshop

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Workshop（代码工坊）线上协议层。
 *
 * 为了让 Android 端解耦于后端 `SubmissionStatus + Verdict` 的双层枚举，
 * Android 仅采用 [SubmissionStatus] 一个扁平枚举：
 *   - PENDING / RUNNING 为非终态，会被 [WorkshopRepository.pollStatus] 持续轮询；
 *   - ACCEPTED / WRONG_ANSWER / TIME_LIMIT / RUNTIME_ERROR 为终态，停止轮询。
 *
 * 后端如需对接，按 ADR-001 §3 决定由 OpenAPI Generator 生成 Wire 层 +
 * 手工补丁层做枚举映射；Android-R9 demo 阶段直接信任后端返回该扁平枚举。
 */

@Serializable
data class ProblemDto(
    @SerialName("problemId")
    val id: Long,
    val kcCode: String,
    val title: String,
    val statementMarkdown: String,
    val inputDescription: String? = null,
    val outputDescription: String? = null,
    val difficulty: String? = null,
    val samples: List<ProblemSampleDto> = emptyList(),
)

@Serializable
data class ProblemSampleDto(
    val input: String,
    val output: String,
)

@Serializable
data class SubmissionDto(
    val id: Long,
    val problemId: Long,
    val userId: Long? = null,
    val code: String,
    val status: SubmissionStatus,
    val submittedAt: String? = null,
    val runtimeMs: Int? = null,
    val memoryKb: Int? = null,
    val passedCases: Int? = null,
    val totalCases: Int? = null,
    val traceback: String? = null,
) {
    val isTerminal: Boolean get() = status.isTerminal
}

@Serializable
data class SubmissionDetailDto(
    val submission: SubmissionDto,
    val judgeResult: JudgeResultDto? = null,
) {
    fun toSubmissionDto(): SubmissionDto =
        submission.copy(
            passedCases = judgeResult?.passedCases ?: submission.passedCases,
            totalCases = judgeResult?.totalCases ?: submission.totalCases,
            traceback = judgeResult?.failureReason ?: submission.traceback,
        )
}

@Serializable
data class JudgeResultDto(
    val verdict: String? = null,
    val passedCases: Int? = null,
    val totalCases: Int? = null,
    val failureReason: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SubmissionCreateRequest(
    val problemId: Long,
    val code: String,
    @EncodeDefault
    val source: String = "TRAINING",
)

@Serializable
enum class SubmissionStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    ERROR,
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT,
    RUNTIME_ERROR,
    ;

    val isTerminal: Boolean
        get() = this == ACCEPTED ||
            this == WRONG_ANSWER ||
            this == TIME_LIMIT ||
            this == RUNTIME_ERROR ||
            this == PASSED ||
            this == FAILED ||
            this == ERROR

    val isSuccess: Boolean
        get() = this == ACCEPTED || this == PASSED

    val displayLabel: String
        get() = when (this) {
            PENDING -> "排队中"
            RUNNING -> "判题中"
            PASSED -> "通过"
            FAILED -> "答案错误"
            ERROR -> "运行错误"
            ACCEPTED -> "通过"
            WRONG_ANSWER -> "答案错误"
            TIME_LIMIT -> "超时"
            RUNTIME_ERROR -> "运行错误"
        }
}
