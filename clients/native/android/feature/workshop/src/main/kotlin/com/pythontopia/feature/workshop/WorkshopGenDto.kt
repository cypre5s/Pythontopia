package com.pythontopia.feature.workshop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 百晓工坊「资源生成」DTO（contract §5：`POST /api/workshop/resources/stream`，SSE plan→trace*→result→error）。
 * 请求体 camelCase（Java record）；SSE result/trace 来自 Python graph，按 snake_case + ignoreUnknownKeys 容错解析。
 */

/** 可生成的 6 类资源（对齐后端 ResourceType）。 */
@Serializable
enum class WorkshopResourceType {
    HANDOUT, MINDMAP, PRACTICE, CODE_LAB, READING, STORYBOARD,
    ;

    val label: String
        get() = when (this) {
            HANDOUT -> "讲义"
            MINDMAP -> "思维导图"
            PRACTICE -> "练习"
            CODE_LAB -> "代码实验"
            READING -> "拓展阅读"
            STORYBOARD -> "故事化讲解"
        }

    companion object {
        fun fromOrNull(raw: String?): WorkshopResourceType? =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() }
    }
}

/** 生成请求体（kcCodes 1..10、type 必填、focusHint 可空 ≤512）。 */
@Serializable
data class ResourceCreateRequestBody(
    val kcCodes: List<String>,
    val type: WorkshopResourceType,
    val focusHint: String? = null,
)

@Serializable
data class WorkshopGenPlanData(
    val steps: List<String> = emptyList(),
    val message: String? = null,
)

@Serializable
data class WorkshopGenTraceData(
    val node: String? = null,
    val agent: String? = null,
    val label: String? = null,
    val summary: String? = null,
    val status: String? = null,
)

@Serializable
data class WorkshopGenResultData(
    val id: Long? = null,
    @SerialName("resource_id") val resourceId: Long? = null,
    val title: String? = null,
    val summary: String? = null,
    @SerialName("grounding_status") val groundingStatus: String? = null,
    @SerialName("safety_status") val safetyStatus: String? = null,
)

@Serializable
data class WorkshopGenErrorData(
    val code: String? = null,
    val message: String? = null,
)

/** 仓库向上游发射的已解析流式事件。 */
sealed interface WorkshopGenEvent {
    data class Plan(val steps: List<String>, val message: String?) : WorkshopGenEvent
    data class Trace(val label: String, val status: String?) : WorkshopGenEvent
    data class Result(
        val title: String,
        val summary: String,
        val resourceId: Long?,
        val groundingStatus: String?,
    ) : WorkshopGenEvent
    data class Failed(val message: String) : WorkshopGenEvent
}

/** 意图种类 → 可生成资源类型；不可生成（如 VIDEO/ASK/REVIEW）返回 null。 */
fun IntentKind.toResourceType(): WorkshopResourceType? = when (this) {
    IntentKind.HANDOUT -> WorkshopResourceType.HANDOUT
    IntentKind.MINDMAP -> WorkshopResourceType.MINDMAP
    IntentKind.PRACTICE -> WorkshopResourceType.PRACTICE
    IntentKind.CODE_LAB -> WorkshopResourceType.CODE_LAB
    IntentKind.READING -> WorkshopResourceType.READING
    IntentKind.STORYBOARD -> WorkshopResourceType.STORYBOARD
    else -> null
}
