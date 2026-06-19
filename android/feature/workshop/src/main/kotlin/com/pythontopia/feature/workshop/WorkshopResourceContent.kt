package com.pythontopia.feature.workshop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 工坊生成物详情 DTO + 多态 content 解析（对齐 Web `dto/Workshop.ts`）。
 * `GET /api/workshop/resources/{id}` 返回 content 随 type 变化的 JSON；按 type 宽松解码为
 * [WorkshopContentView] 供 UI 渲染（思维导图/PPT/代码实验/拓展阅读/故事化讲解/讲义）。
 */

@Serializable
data class WorkshopResourceDetailDto(
    val id: Long,
    val type: WorkshopResourceType = WorkshopResourceType.HANDOUT,
    val title: String = "",
    val summary: String? = null,
    val kcCodes: List<String> = emptyList(),
    val content: JsonElement? = null,
    val evidencePageIds: List<Long> = emptyList(),
    val groundingStatus: String? = null,
)

// ── 各类型 content（snake_case 对齐 graph 输出；省略 page_refs/sample_io 等以提高解码鲁棒性） ──

@Serializable
data class WsHandoutContent(val sections: List<WsHandoutSection> = emptyList())

@Serializable
data class WsHandoutSection(
    val heading: String = "",
    val paragraphs: List<String> = emptyList(),
    @SerialName("code_snippets") val codeSnippets: List<String> = emptyList(),
)

@Serializable
data class WsMindMapContent(
    val mermaid: String = "",
    @SerialName("kc_map") val kcMap: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class WsPracticeContent(val problems: List<WsProblemSpec> = emptyList())

@Serializable
data class WsProblemSpec(
    val title: String = "",
    @SerialName("statement_md") val statementMd: String = "",
    val hints: List<String> = emptyList(),
    @SerialName("kc_codes") val kcCodes: List<String> = emptyList(),
    val difficulty: String = "",
)

@Serializable
data class WsCodeLabContent(val walkthroughs: List<WsWalkthrough> = emptyList())

@Serializable
data class WsWalkthrough(
    val title: String = "",
    val narrative: String = "",
    val code: String = "",
    @SerialName("expected_output") val expectedOutput: String = "",
    @SerialName("common_mistakes") val commonMistakes: List<String> = emptyList(),
)

@Serializable
data class WsReadingContent(val items: List<WsReadingItem> = emptyList())

@Serializable
data class WsReadingItem(
    @SerialName("summary_md") val summaryMd: String = "",
    @SerialName("kc_codes") val kcCodes: List<String> = emptyList(),
)

@Serializable
data class WsStoryboardContent(val scenes: List<WsScene> = emptyList())

@Serializable
data class WsScene(
    val title: String = "",
    val narration: String = "",
    @SerialName("visual_hint") val visualHint: String = "",
    @SerialName("kc_codes") val kcCodes: List<String> = emptyList(),
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
)

/** 解析后的 content 视图（供 Compose 渲染）。 */
sealed interface WorkshopContentView {
    data class Handout(val sections: List<WsHandoutSection>) : WorkshopContentView
    data class MindMap(val mermaid: String, val kcMap: Map<String, List<String>>) : WorkshopContentView
    data class Practice(val problems: List<WsProblemSpec>) : WorkshopContentView
    data class CodeLab(val walkthroughs: List<WsWalkthrough>) : WorkshopContentView
    data class Reading(val items: List<WsReadingItem>) : WorkshopContentView
    data class Storyboard(val scenes: List<WsScene>) : WorkshopContentView
    data object Unsupported : WorkshopContentView
}
