package com.pythontopia.feature.mine

import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 记忆矿脉「遗忘回声」+ 错题本 + 富会话详情 DTO（contract §4.7）。
 *
 * 与 [MineDto] 的轻量「失败提交 → 单卡 solve」模型并行：本文件承载 FSRS 到期复盘闭环
 * （`GET/POST /api/bug-mine/echoes`、`GET /api/bug-mine/errorbook`、`GET /api/bug-mine/sessions/{id}`
 * 的完整字段：depthLayer / depthSteps / 5 张 reveal 卡 / 题干 / L5 变式）。wire 适配在 [MineRepository]。
 */

/** `POST /api/bug-mine/echoes` 请求体；用 FSRS 到期 KC 打开遗忘回声会话。 */
@Serializable
data class MineEchoOpenRequest(
    val kcCode: String,
)

/**
 * 遗忘回声候选 KC（`GET /api/bug-mine/echoes` 单项）。
 * [minePriority] / [decayRisk] 为 0..1，UI 以百分比展示；decayRisk = 1 − R（可提取性）。
 */
data class EchoCandidate(
    val kcCode: String,
    val problemId: Long?,
    val problemTitle: String,
    val masteryScore: Double,
    val confidence: Double,
    val decayRisk: Double,
    val minePriority: Double,
    val recommendationReason: String,
) {
    /** 入矿推荐度（minePriority * 100，四舍五入）。 */
    val recommendationPercent: Int get() = (minePriority * PERCENT).roundToInt().coerceIn(0, PERCENT.toInt())

    /** 遗忘风险百分比（decayRisk * 100）。 */
    val decayPercent: Int get() = (decayRisk * PERCENT).roundToInt().coerceIn(0, PERCENT.toInt())
}

/** 错题本未关闭会话条目（`GET /api/bug-mine/errorbook` 单项）。 */
data class ErrorBookItem(
    val sessionId: Long,
    val submissionId: Long?,
    val problemId: Long?,
    val questId: Long?,
    val kcCode: String,
    val problemTitle: String,
    val status: String,
    val retryCount: Int,
    val depthLayer: Int,
    val profileDepthTarget: Int,
    val profileGuidance: String?,
    val masteryScore: Double,
    val minePriority: Double,
    val decayRisk: Double,
    val recommendationReason: String,
    val mistakePatterns: List<String>,
    val patternMatched: Boolean,
    val variantProblem: MineVariantProblem?,
    val depthSteps: List<MineDepthStep>,
) {
    val recommendationPercent: Int get() = (minePriority * PERCENT).roundToInt().coerceIn(0, PERCENT.toInt())
    val decayPercent: Int get() = (decayRisk * PERCENT).roundToInt().coerceIn(0, PERCENT.toInt())
}

/** 五层深度步骤（L1 裸重试 … L5 同 KC 变式）；[unlocked] = layer ≤ 当前 depthLayer。 */
data class MineDepthStep(
    val layer: Int,
    val key: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
)

/** L5 同 KC 变式题引用。 */
data class MineVariantProblem(
    val id: Long,
    val title: String,
)

/** 题面样例对。 */
data class MineProblemSample(
    val input: String,
    val output: String,
)

/** 随会话一次性下发的题干（来自后端 PublicProblemDto），免客户端再请求单题。 */
data class MineSessionProblem(
    val id: Long?,
    val kcCode: String,
    val title: String,
    val statementMarkdown: String,
    val inputDescription: String?,
    val outputDescription: String?,
    val difficulty: String?,
    val samples: List<MineProblemSample>,
)

/**
 * reveal 门控的深度卡。[requiredDepth] 为后端深度门槛（COUNTER_EXAMPLE=2 / VARIABLE_TRACE=3 /
 * WRONG_ASSUMPTION+HINT_*=4）；未 reveal 时 [body] 不展示，仅当 depthLayer ≥ requiredDepth 才允许解锁。
 */
data class MineSessionCard(
    val id: Long,
    val cardType: String,
    val title: String,
    val requiredDepth: Int,
    val revealed: Boolean,
    val body: String,
    val expectedOutput: String?,
    val kcCode: String?,
)

/** 富会话详情（`GET /api/bug-mine/sessions/{id}` / `POST /api/bug-mine/echoes`）。 */
data class MineSessionDetail(
    val sessionId: Long,
    val status: String,
    val submissionId: Long?,
    val problemId: Long?,
    val retryCount: Int,
    val depthLayer: Int,
    val profileDepthTarget: Int,
    val masteryConfirmed: Boolean,
    val profileGuidance: String?,
    val variantProblem: MineVariantProblem?,
    val depthSteps: List<MineDepthStep>,
    val cards: List<MineSessionCard>,
    val problem: MineSessionProblem?,
) {
    /** 会话仍可提交。 */
    val isOpen: Boolean get() = status == STATUS_OPEN

    /** 已下探到 L5 且存在变式题：可提交同 KC 变式以确认掌握。 */
    val canSubmitVariant: Boolean get() = isOpen && depthLayer >= MAX_DEPTH && variantProblem != null

    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_RESOLVED = "RESOLVED"
        const val STATUS_ABANDONED = "ABANDONED"
        const val MAX_DEPTH = 5
    }
}

// —— wire DTOs（后端 camelCase record / enum 名）——

@Serializable
internal data class BugMineEchoCandidateWireDto(
    val kcCode: String,
    val problemId: Long? = null,
    val problemTitle: String = "",
    val masteryScore: Double? = null,
    val confidence: Double? = null,
    val decayRisk: Double? = null,
    val minePriority: Double? = null,
    val recommendationReason: String = "",
) {
    fun toDomain(): EchoCandidate =
        EchoCandidate(
            kcCode = kcCode,
            problemId = problemId,
            problemTitle = problemTitle.ifBlank { kcCode },
            masteryScore = masteryScore ?: 0.0,
            confidence = confidence ?: 0.0,
            decayRisk = decayRisk ?: 0.0,
            minePriority = minePriority ?: 0.0,
            recommendationReason = recommendationReason,
        )
}

@Serializable
internal data class BugMineErrorBookItemWireDto(
    val sessionId: Long,
    val submissionId: Long? = null,
    val problemId: Long? = null,
    val questId: Long? = null,
    val kcCode: String = "",
    val problemTitle: String = "",
    val status: String = MineSessionDetail.STATUS_OPEN,
    val retryCount: Int = 0,
    val depthLayer: Int = 1,
    val profileDepthTarget: Int = 1,
    val profileGuidance: String? = null,
    val masteryScore: Double? = null,
    val minePriority: Double? = null,
    val decayRisk: Double? = null,
    val recommendationReason: String = "",
    val mistakePatterns: List<String> = emptyList(),
    val patternMatched: Boolean = false,
    val variantProblem: BugMineVariantProblemWireDto? = null,
    val depthSteps: List<BugMineDepthStepWireDto> = emptyList(),
) {
    fun toDomain(): ErrorBookItem =
        ErrorBookItem(
            sessionId = sessionId,
            submissionId = submissionId,
            problemId = problemId,
            questId = questId,
            kcCode = kcCode,
            problemTitle = problemTitle.ifBlank { kcCode },
            status = status,
            retryCount = retryCount,
            depthLayer = depthLayer,
            profileDepthTarget = profileDepthTarget,
            profileGuidance = profileGuidance,
            masteryScore = masteryScore ?: 0.0,
            minePriority = minePriority ?: 0.0,
            decayRisk = decayRisk ?: 0.0,
            recommendationReason = recommendationReason,
            mistakePatterns = mistakePatterns,
            patternMatched = patternMatched,
            variantProblem = variantProblem?.toDomain(),
            depthSteps = depthSteps.map { it.toDomain() },
        )
}

@Serializable
internal data class BugMineDepthStepWireDto(
    val layer: Int = 0,
    val key: String = "",
    val title: String = "",
    val description: String = "",
    val unlocked: Boolean = false,
) {
    fun toDomain(): MineDepthStep =
        MineDepthStep(layer = layer, key = key, title = title, description = description, unlocked = unlocked)
}

@Serializable
internal data class BugMineVariantProblemWireDto(
    val id: Long,
    val title: String? = null,
) {
    fun toDomain(): MineVariantProblem = MineVariantProblem(id = id, title = title.orEmpty().ifBlank { "同 KC 变式题 #$id" })
}

@Serializable
internal data class BugMinePublicProblemWireDto(
    val id: Long? = null,
    val kcCode: String? = null,
    val title: String? = null,
    val statementMarkdown: String? = null,
    val inputDescription: String? = null,
    val outputDescription: String? = null,
    val difficulty: String? = null,
    val samples: JsonElement? = null,
) {
    fun toDomain(): MineSessionProblem =
        MineSessionProblem(
            id = id,
            kcCode = kcCode.orEmpty(),
            title = title.orEmpty().ifBlank { "调试题" },
            statementMarkdown = statementMarkdown.orEmpty(),
            inputDescription = inputDescription,
            outputDescription = outputDescription,
            difficulty = difficulty,
            samples = samples.toSamples(),
        )
}

@Serializable
internal data class BugMineSessionDetailWireDto(
    val id: Long,
    val userId: Long? = null,
    val submissionId: Long? = null,
    val problemId: Long? = null,
    val questId: Long? = null,
    val status: String = MineSessionDetail.STATUS_OPEN,
    val retryCount: Int = 0,
    val depthLayer: Int = 1,
    val profileDepthTarget: Int = 1,
    val masteryConfirmed: Boolean = false,
    val variantSubmissionId: Long? = null,
    val profileGuidance: String? = null,
    val variantProblem: BugMineVariantProblemWireDto? = null,
    val depthSteps: List<BugMineDepthStepWireDto> = emptyList(),
    val cards: List<BugMineCardWireDto> = emptyList(),
    val problem: BugMinePublicProblemWireDto? = null,
) {
    fun toDomain(): MineSessionDetail =
        MineSessionDetail(
            sessionId = id,
            status = status,
            submissionId = submissionId,
            problemId = problemId,
            retryCount = retryCount,
            depthLayer = depthLayer.coerceIn(1, MineSessionDetail.MAX_DEPTH),
            profileDepthTarget = profileDepthTarget.coerceIn(1, MineSessionDetail.MAX_DEPTH),
            masteryConfirmed = masteryConfirmed,
            profileGuidance = profileGuidance,
            variantProblem = variantProblem?.toDomain(),
            depthSteps = depthSteps.map { it.toDomain() },
            cards = cards.map { it.toSessionCard() },
            problem = problem?.toDomain(),
        )
}

/** 把通用矿洞卡映射为 reveal 门控的深度卡（复用 [BugMineCardWireDto]，仅本闭环用富展示）。 */
internal fun BugMineCardWireDto.toSessionCard(): MineSessionCard =
    MineSessionCard(
        id = id,
        cardType = cardType,
        title = sessionCardTitle(cardType),
        requiredDepth = depthForCardType(cardType),
        revealed = revealed,
        body = if (revealed) sessionCardBody(cardType, payload) else "",
        expectedOutput = payload.str("expected_stdout", "expected"),
        kcCode = payload.arr("evidence_kc_codes").firstOrNull()
            ?: payload.arr("expected_kc_codes").firstOrNull(),
    )

private fun sessionCardTitle(type: String): String = when (type) {
    "COUNTER_EXAMPLE" -> "反例矿脉（L2）"
    "VARIABLE_TRACE" -> "变量轨迹（L3）"
    "WRONG_ASSUMPTION" -> "错误假设（L4）"
    "HINT_LAYER_1" -> "一层提示（L4）"
    "HINT_LAYER_2" -> "二层提示（L4）"
    "HINT_LAYER_3" -> "三层提示（L4）"
    else -> type
}

private fun depthForCardType(type: String): Int = when (type) {
    "COUNTER_EXAMPLE" -> 2
    "VARIABLE_TRACE" -> 3
    "WRONG_ASSUMPTION", "HINT_LAYER_1", "HINT_LAYER_2", "HINT_LAYER_3" -> 4
    else -> 1
}

/** 卡片正文按类型做可读化提取；payload 形态由 DebugAgent 决定，故做容错多键回退。 */
private fun sessionCardBody(type: String, payload: JsonObject): String {
    if (type == "VARIABLE_TRACE") {
        val steps = (payload["steps"] as? JsonArray)?.size ?: 0
        val text = payload.str("summary", "body", "text")
        return buildString {
            if (steps > 0) append("共记录 $steps 步变量轨迹。")
            if (!text.isNullOrBlank()) {
                if (isNotEmpty()) append('\n')
                append(text)
            }
            if (isEmpty()) append("（运行轨迹已采集，沿执行顺序检查变量取值。）")
        }
    }
    if (type == "COUNTER_EXAMPLE") {
        val diff = payload.str("diff_summary", "summary")
        val stdin = payload.str("stdin", "input")
        val expected = payload.str("expected_stdout", "expected")
        val actual = payload.str("actual", "actually", "actual_stdout")
        return buildString {
            if (!stdin.isNullOrBlank()) append("输入：\n$stdin\n")
            if (!expected.isNullOrBlank()) append("期望：\n$expected\n")
            if (!actual.isNullOrBlank()) append("实际：\n$actual\n")
            if (!diff.isNullOrBlank()) append(diff)
            if (isEmpty()) append("（已挑出第一个失败样例，对照期望与实际输出。）")
        }.trim()
    }
    return payload.str("body", "text", "hint", "message", "explanation", "assumption", "content", "summary")
        ?: "（提示内容已解锁，仔细阅读后独立修复。）"
}

private fun JsonObject.str(vararg keys: String): String? {
    for (key in keys) {
        val value = (this[key] as? JsonPrimitive)?.contentOrNull
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun JsonObject.arr(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

private fun JsonElement?.toSamples(): List<MineProblemSample> {
    val array = this as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val input = (obj["input"] as? JsonPrimitive)?.contentOrNull
        val output = (obj["output"] as? JsonPrimitive)?.contentOrNull
        if (input != null && output != null) MineProblemSample(input, output) else null
    }
}

private const val PERCENT = 100.0
