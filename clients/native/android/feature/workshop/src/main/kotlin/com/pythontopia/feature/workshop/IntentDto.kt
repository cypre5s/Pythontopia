package com.pythontopia.feature.workshop

import kotlinx.serialization.Serializable

/**
 * 百晓工坊「意图路由」DTO（contract §5.1，非流式）。后端 `POST /api/intent/route` 把一句话分类成
 * 9 类意图并锚定 KC；客户端据 [IntentKind] 分派：ASK→导师、REVIEW→记忆矿脉回声、其余→工坊生成。
 * 后端 `intent` 是 String 透传，故此处也用 String + 容错枚举，避免随枚举改动。
 */

/** 请求体（前端 camelCase，除 message 外均可空）。 */
@Serializable
data class IntentRouteRequestBody(
    val message: String,
    val manualKcCodes: List<String>? = null,
    val courseKcs: List<IntentCourseKc>? = null,
    val currentChapter: Int? = null,
    val recentKcCodes: List<String>? = null,
)

/** 课程 KC 目录条目：code 用于锚定，name 帮助 LLM 映射主题。 */
@Serializable
data class IntentCourseKc(
    val code: String,
    val name: String? = null,
)

/** 响应：intent + 锚定 KC + 一句话学习重点 + 置信度 + 理由。 */
@Serializable
data class IntentRouteResponse(
    val intent: String = "",
    val kcCodes: List<String> = emptyList(),
    val focusHint: String? = null,
    val confidence: Double = 0.0,
    val reason: String? = null,
) {
    /** 容错解析后的意图种类。 */
    val kind: IntentKind get() = IntentKind.from(intent)
}

/**
 * 意图种类（contract §5.1 枚举的并集 + [UNKNOWN] 兜底）。
 * [UNKNOWN] 不直接分派为资源生成，按 ASK 兜底到导师，避免误路由。
 */
enum class IntentKind {
    ASK,
    VIDEO,
    REVIEW,
    HANDOUT,
    MINDMAP,
    PRACTICE,
    CODE_LAB,
    READING,
    STORYBOARD,
    UNKNOWN,
    ;

    /** 面向学生的中文标签。 */
    val label: String
        get() = when (this) {
            ASK -> "导师答疑"
            VIDEO -> "精选讲解视频"
            REVIEW -> "记忆矿脉回声复盘"
            HANDOUT -> "讲义生成"
            MINDMAP -> "思维导图"
            PRACTICE -> "练习生成"
            CODE_LAB -> "代码实验"
            READING -> "拓展阅读"
            STORYBOARD -> "故事化讲解"
            UNKNOWN -> "未识别意图"
        }

    companion object {
        fun from(raw: String): IntentKind =
            entries.firstOrNull { it.name == raw.trim().uppercase() } ?: UNKNOWN
    }
}

/**
 * 客户端分派目标（与 contract §5.1 一一对应）：
 * - [OpenTutor]：导师答疑（`/api/tutor/ask` SSE）。
 * - [OpenReview]：记忆矿脉回声复盘（去 Bug 矿洞按 FSRS 到期 KC 复盘，**不**调工坊生成）。
 * - [Generate]：6 类资源 + 视频 → 工坊生成管线。
 */
sealed interface IntentDispatch {
    val kcCodes: List<String>
    val focusHint: String?

    data class OpenTutor(
        override val kcCodes: List<String>,
        override val focusHint: String?,
    ) : IntentDispatch

    data class OpenReview(
        override val kcCodes: List<String>,
        override val focusHint: String?,
    ) : IntentDispatch

    data class Generate(
        val kind: IntentKind,
        override val kcCodes: List<String>,
        override val focusHint: String?,
    ) : IntentDispatch
}

/**
 * 意图 → 分派。REVIEW 单独走记忆矿脉回声，**绝不**当作资源生成误路由；UNKNOWN 兜底到导师。
 */
fun IntentRouteResponse.toDispatch(): IntentDispatch = when (kind) {
    IntentKind.REVIEW -> IntentDispatch.OpenReview(kcCodes, focusHint)
    IntentKind.ASK, IntentKind.UNKNOWN -> IntentDispatch.OpenTutor(kcCodes, focusHint)
    IntentKind.VIDEO,
    IntentKind.HANDOUT,
    IntentKind.MINDMAP,
    IntentKind.PRACTICE,
    IntentKind.CODE_LAB,
    IntentKind.READING,
    IntentKind.STORYBOARD,
    -> IntentDispatch.Generate(kind, kcCodes, focusHint)
}
