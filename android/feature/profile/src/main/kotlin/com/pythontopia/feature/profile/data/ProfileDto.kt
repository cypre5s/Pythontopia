package com.pythontopia.feature.profile.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Pythontopia 学生画像 (Android 端) DTO。
 *
 * 后端权威：`backend/com/pythontopia/learner/profile/dto/LearnerProfileDto.java`
 * + `BaseLevelDto/LearningGoalDto/...`，字段名经 Jackson 默认驼峰序列化。
 *
 * 8 维 `dimensions` JSONB（被动聚合，PUT 走的是这套 key）与 8 个结构化子字段
 * （interview agent 生成，GET 直接返回）是两层；本文件覆盖后者，前者通过
 * [DimensionUpdateRequest] / [DimensionUpdateResult] + [ProfileDimensions] 操作。
 */
@Serializable
data class BaseLevelDto(
    val programmingYears: Int? = null,
    val languagesKnown: List<String>? = null,
    val pythonSelfRatedLevel: Int? = null,
)

@Serializable
data class LearningGoalDto(
    val kind: String? = null,
    val deadlineIso: String? = null,
    val targetKcCodes: List<String>? = null,
)

@Serializable
data class CognitiveStyleDto(
    val fieldDependent: Double? = null,
    val sequentialVsHolistic: Double? = null,
)

@Serializable
data class LearningPreferenceDto(
    val mediaPriority: List<String>? = null,
    val sessionMinutesPerDay: Int? = null,
)

@Serializable
data class ErrorPatternsDto(
    val frequentKcCodes: List<String>? = null,
    val commonCategories: List<String>? = null,
)

@Serializable
data class PracticeCapabilityDto(
    val avgAttemptsPerProblem: Double? = null,
    val successRate: Double? = null,
)

@Serializable
data class EmotionPersistenceDto(
    val resilience: Double? = null,
    val recentMood: String? = null,
)

@Serializable
data class LearnerProfileDto(
    val profileId: Long? = null,
    val userId: Long? = null,
    val packId: Long? = null,
    val major: String? = null,
    val baseLevel: BaseLevelDto? = null,
    val learningGoal: LearningGoalDto? = null,
    val cognitiveStyle: CognitiveStyleDto? = null,
    val learningPreference: LearningPreferenceDto? = null,
    val errorPatterns: ErrorPatternsDto? = null,
    val practiceCapability: PracticeCapabilityDto? = null,
    val emotionPersistence: EmotionPersistenceDto? = null,
    val narrativeSummary: String? = null,
)

/**
 * `PUT /api/learner/profile/dimensions/{key}` 请求体。
 * 后端 [SelfDimensionRequest] 校验：scoreDelta ∈ [-1, 1]，至少一字段非空。
 */
@Serializable
data class DimensionUpdateRequest(
    val scoreDelta: Double? = null,
    val evidenceIncrement: Int? = null,
    val tagsAdd: List<String>? = null,
    val metadata: JsonObject? = null,
)

/**
 * `PUT /api/learner/profile/dimensions/{key}` 响应：写入后的该维度完整快照。
 * `finalDimension` 是后端 JSONB 直传，结构由维度自行定义（含 score / evidence_count / tags / metadata）。
 */
@Serializable
data class DimensionUpdateResult(
    val userId: Long,
    val dimensionKey: String,
    val finalDimension: JsonElement,
)

/**
 * `GET /api/learner/profile/dimensions` 列表项：单个维度的对外快照。
 *
 * 后端权威：`backend/com/pythontopia/learner/profile/dto/ProfileDimensionDto.java`（Jackson 默认驼峰）。
 * 该端点**恒返回 8 维**（[ProfileDimensions.KEYS] 顺序），缺失维度由后端按 `score=0.5 / evidence=0` 兜底，
 * 因此不会出现 204；HTTP 错误由 Retrofit 抛 `HttpException`，在 [ProfileRepository] 归一为 [com.pythontopia.core.common.Result.Failure]。
 *
 * 字段语义：
 *  - [score] 0..1 已做读时 decay 的 effective 值（无信号时为 0.5 中性默认）。
 *  - [confidence] 0..1 = min(1, evidenceCount / 8)，越低代表信号越弱、越不应强断言。
 *  - [evidenceCount] 累计证据条数；为 0 表示该维度尚无真实信号。
 *  - [selfEditable] 学生是否可经 `PUT scoreDelta` 自评直写该维度（权威以后端为准）。
 *  - [tags] 该维度累积标签（如媒介偏好 video/visual）。
 *
 * 全部给默认值是为了在后端偶发省略字段时仍能反序列化（`explicitNulls=false` 容错）。
 */
@Serializable
data class ProfileDimensionDto(
    val key: String,
    val label: String = "",
    val score: Double = 0.5,
    val confidence: Double = 0.0,
    val evidenceCount: Int = 0,
    val selfEditable: Boolean = false,
    val tags: List<String> = emptyList(),
)

/**
 * 8 维 key 的 Android 端权威清单，与后端 `PassiveProfileService.DIMENSION_KEYS` 保持一致。
 *
 * - [KEYS]：写 `learner_profile.dimensions` JSONB 使用的 key（snake_case，单数）。
 * - [SELF_EDITABLE]：学生 PUT 自评接口允许直写的 4 主观维度。
 * - [LABELS]：UI 展示的中文标签。
 */
object ProfileDimensions {

    const val PROFESSIONAL_BACKGROUND = "professional_background"
    const val KNOWLEDGE_BASE = "knowledge_base"
    const val LEARNING_GOAL = "learning_goal"
    const val COGNITIVE_STYLE = "cognitive_style"
    const val LEARNING_PREFERENCE = "learning_preference"
    const val ERROR_PATTERN = "error_pattern"
    const val PRACTICAL_ABILITY = "practical_ability"
    const val EMOTION_PERSISTENCE = "emotion_persistence"

    val KEYS: List<String> = listOf(
        PROFESSIONAL_BACKGROUND,
        KNOWLEDGE_BASE,
        LEARNING_GOAL,
        COGNITIVE_STYLE,
        LEARNING_PREFERENCE,
        ERROR_PATTERN,
        PRACTICAL_ABILITY,
        EMOTION_PERSISTENCE,
    )

    val SELF_EDITABLE: Set<String> = setOf(
        PROFESSIONAL_BACKGROUND,
        LEARNING_GOAL,
        LEARNING_PREFERENCE,
        EMOTION_PERSISTENCE,
    )

    val LABELS: Map<String, String> = mapOf(
        PROFESSIONAL_BACKGROUND to "专业背景",
        KNOWLEDGE_BASE to "知识基础",
        LEARNING_GOAL to "学习目标",
        COGNITIVE_STYLE to "认知风格",
        LEARNING_PREFERENCE to "学习偏好",
        ERROR_PATTERN to "易错模式",
        PRACTICAL_ABILITY to "实践能力",
        EMOTION_PERSISTENCE to "情绪与坚持度",
    )

    fun isSelfEditable(key: String): Boolean = key in SELF_EDITABLE

    fun label(key: String): String = LABELS[key] ?: key
}
