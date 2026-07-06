package com.pythontopia.feature.feynman

import kotlinx.serialization.Serializable

/**
 * 费曼反演 DTO，对齐后端 `/api/feynman` 系列端点（`FeynmanController` / `FeynmanDtos`）。
 *
 * 关键契约（与 Web `api/feynman.ts` + `stores/feynman.ts` 保持一致）：
 * - 会话 [tuteeState] 持久化形态恒为 `{"misconceptions":[...]}`，`producedCode`/`judgeResult` 由端侧推导。
 * - 后端 `TurnResponse.taughtFix` 实际承载学弟「产出代码」，故端侧取 producedCode ?: taughtFix。
 * - 判题 `Verdict` 仅含 passed/message；证据 event_id 回链到当前误解的真实事件（零合成）。
 */

@Serializable
data class StartFeynmanSessionRequest(val kcCode: String)

@Serializable
data class TeachFeynmanSessionRequest(val explanation: String)

@Serializable
data class FeynmanMisconceptionDto(
    val tag: String = "",
    val evidenceEventIds: List<Long> = emptyList(),
    val resolved: Boolean = false,
)

@Serializable
data class FeynmanJudgeResultDto(
    val status: String = "PENDING",
    val messageZh: String = "",
    val evidenceEventIds: List<Long> = emptyList(),
)

@Serializable
data class FeynmanTuteeStateDto(
    val misconceptions: List<FeynmanMisconceptionDto> = emptyList(),
    val producedCode: String? = null,
    val judgeResult: FeynmanJudgeResultDto? = null,
)

@Serializable
data class FeynmanSessionDto(
    val id: Long = 0L,
    val userId: Long = 0L,
    val kcCode: String = "",
    val status: String = "OPEN",
    val tuteeState: FeynmanTuteeStateDto = FeynmanTuteeStateDto(),
)

@Serializable
data class FeynmanTurnDto(
    val id: Long = 0L,
    val role: String = "TUTEE",
    val content: String = "",
    val taughtFix: String? = null,
    val producedCode: String? = null,
    val judgeResult: FeynmanJudgeResultDto? = null,
)

@Serializable
data class FeynmanVerdictDto(
    val sessionId: Long = 0L,
    val passed: Boolean = false,
    val message: String = "",
)

/**
 * 代码找茬样本：学弟写出的含 bug 代码 + bug 行号（后端由 buggy/fixed diff 得出，仅供客户端判分）。
 * 对齐 Web `FeynmanBugHuntDto`（`POST /api/feynman/sessions/{id}/bug-hunt`）。
 */
@Serializable
data class FeynmanBugHuntDto(
    val source: String = "none",
    val misconceptionTag: String? = null,
    val buggyCode: String? = null,
    val bugLineIndices: List<Int> = emptyList(),
    val hintMd: String = "",
)
