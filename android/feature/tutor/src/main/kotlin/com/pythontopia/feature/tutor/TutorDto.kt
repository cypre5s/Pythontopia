package com.pythontopia.feature.tutor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 导师问答 DTO。
 *
 * 两套命名来源（务必区分）：
 *  - `POST /api/tutor/ask` 请求体 + `GET /api/tutor/sessions` 响应：后端 Java record → camelCase。
 *  - SSE `result` 事件 data：直接来自 Python graph `response.model_dump()` → snake_case。
 */

/** 提问请求体（后端 [TutorAskRequest] record，camelCase）。 */
@Serializable
data class TutorAskRequestBody(
    val question: String,
    val kcCodes: List<String>? = null,
    val context: String? = null,
)

/** 历史会话（后端 [TutorSessionDto] record，camelCase）。 */
@Serializable
data class TutorSessionDto(
    val id: Long,
    val question: String,
    val answerMd: String? = null,
    val evidencePageIds: List<Int> = emptyList(),
    val groundingStatus: String? = null,
    val createdAt: String? = null,
)

/** SSE `token` 事件 data：增量文本。 */
@Serializable
data class TutorTokenData(
    val delta: String = "",
)

/** SSE `result` 事件 data（来自 Python graph，snake_case）。 */
@Serializable
data class TutorResultData(
    @SerialName("answer_md") val answerMd: String? = null,
    @SerialName("evidence_page_ids") val evidencePageIds: List<Int> = emptyList(),
    @SerialName("grounding_status") val groundingStatus: String? = null,
)

/** SSE `error` 事件 data。 */
@Serializable
data class TutorErrorData(
    val code: String? = null,
    val message: String? = null,
)

/**
 * 仓库向上游发射的流式事件（已解析）。Screen/ViewModel 据此渲染增量回答。
 */
sealed interface TutorStreamEvent {
    /** 增量 token，追加到当前回答。 */
    data class Token(val delta: String) : TutorStreamEvent

    /** 终态：完整回答 + 证据页 + grounding 评级。 */
    data class Result(
        val answerMd: String,
        val evidencePageIds: List<Int>,
        val groundingStatus: String?,
    ) : TutorStreamEvent

    /** 上游错误。 */
    data class Failed(val message: String) : TutorStreamEvent
}

/** grounding 评级 → 面向学生的中文标签（与 Web EvidenceTag 语义一致）。 */
fun groundingLabel(status: String?): String = when (status?.uppercase()) {
    "PASSED" -> "课件佐证"
    "NO_EVIDENCE" -> "基于通识知识"
    "FAILED" -> "证据校验未通过"
    else -> ""
}
