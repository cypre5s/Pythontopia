package com.pythontopia.feature.lecture

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 讲义阅读 DTO，对齐后端 ResourceWorkshopController（/api/workshop/resources）：
 *  - GET /api/workshop/resources?type=HANDOUT → [LectureListItemDto]
 *  - GET /api/workshop/resources/{id}         → [LectureResourceDto]
 *
 * content 为多态 JSON（HANDOUT/MINDMAP/...），用 [JsonElement] 承接后再宽松解码为 [HandoutContent]。
 * handout 结构对齐 graph 输出（snake_case：code_snippets / page_refs），与 Web HandoutView 一致。
 */

@Serializable
data class LectureListItemDto(
    val id: Long,
    val type: String = "HANDOUT",
    val title: String = "",
    val summary: String = "",
    val kcCodes: List<String> = emptyList(),
    val groundingStatus: String? = null,
    val feedbackStatus: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class LectureResourceDto(
    val id: Long,
    val type: String = "HANDOUT",
    val title: String = "",
    val summary: String = "",
    val kcCodes: List<String> = emptyList(),
    val content: JsonElement? = null,
    val evidencePageIds: List<Long> = emptyList(),
    val groundingStatus: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class HandoutContent(
    val sections: List<HandoutSection> = emptyList(),
)

@Serializable
data class HandoutSection(
    val heading: String = "",
    val paragraphs: List<String> = emptyList(),
    @SerialName("code_snippets")
    val codeSnippets: List<String> = emptyList(),
    @SerialName("page_refs")
    val pageRefs: List<Int> = emptyList(),
)

/** grounding 状态 → 学生可读标签（与 Web EvidenceTag 语义一致）。 */
fun groundingLabel(status: String?): String = when (status) {
    "PASSED" -> "课件佐证"
    "NO_EVIDENCE" -> "通识知识"
    "FAILED" -> "证据待补"
    else -> ""
}
