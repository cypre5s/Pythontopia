package com.pythontopia.feature.mine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bug 矿洞 DTO；wire 适配在 [MineRepository]。
 * `POST /sessions`→[MineSession]；`GET /sessions/{id}`→[MineCard]；`POST /retry-submit`→[MineAttempt]。
 */

/**
 * 副本会话快照。
 * [entry] 取值 FAILED_SUBMISSION / CLASSROOM_QUEST / IDLE_RECOMMEND，决定选卡策略。
 */
@Serializable
data class MineSession(
    val sessionId: Long,
    val entry: String = ENTRY_IDLE_RECOMMEND,
    @SerialName("sourceSubmissionId") val sourceSubmissionId: Long? = null,
    val currentLayer: Int = 1,
    val totalCards: Int = 5,
    val status: String = "OPEN",
) {
    companion object {
        const val ENTRY_FAILED_SUBMISSION = "FAILED_SUBMISSION"
        const val ENTRY_CLASSROOM_QUEST = "CLASSROOM_QUEST"
        const val ENTRY_IDLE_RECOMMEND = "IDLE_RECOMMEND"
    }
}

/** 单张谜题卡；[snippet] 为待修复代码，[expectedOutput] 为判题目标 stdout（含末尾换行）。 */
@Serializable
data class MineCard(
    val id: Long,
    val deckCardId: String,
    val layer: Int,
    val title: String,
    val snippet: String,
    val expectedOutput: String,
    val kcCode: String,
    val pythocoReward: Int = 0,
    val revealed: Boolean = true,
)

/** 判题结果；[status] 为 PASS/FAIL/RUNTIME_ERROR/TIMEOUT。 */
@Serializable
data class MineAttempt(
    val cardId: Long,
    val code: String,
    val status: String,
    val actualOutput: String? = null,
    val message: String? = null,
) {
    companion object {
        const val STATUS_PASS = "PASS"
        const val STATUS_FAIL = "FAIL"
        const val STATUS_RUNTIME_ERROR = "RUNTIME_ERROR"
        const val STATUS_TIMEOUT = "TIMEOUT"
    }
}

/** 开副本请求体；须关联失败提交。 */
@Serializable
data class MineStartRequest(
    @SerialName("submissionId") val submissionId: Long,
)

/** 重试提交请求体。 */
@Serializable
data class MineAttemptRequest(
    val code: String,
)
