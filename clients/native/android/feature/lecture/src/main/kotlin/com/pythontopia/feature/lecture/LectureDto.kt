package com.pythontopia.feature.lecture

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 讲义 DTO：`/api/workshop/resources?type=HANDOUT` 列表与 `/{id}` 详情；content 多态 JSON，handout 段 snake_case 对齐 graph。 */

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

/** 章节视频讲解条目（`GET /api/course/videos?chapterId=`）；字节流 `/api/course/videos/{id}` 放行匿名 + Range。 */
@Serializable
data class CourseVideoDto(
    val id: Long,
    val chapter: Int = 0,
    val title: String = "",
    val durationSec: Int? = null,
    val kcCodes: List<String> = emptyList(),
    val available: Boolean = false,
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
