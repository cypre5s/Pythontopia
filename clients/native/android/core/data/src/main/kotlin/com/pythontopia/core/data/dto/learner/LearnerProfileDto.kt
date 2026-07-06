package com.pythontopia.core.data.dto.learner

import kotlinx.serialization.Serializable

/**
 * 8 维学习画像（结构化版本），对应后端 `LearnerProfileDto`。
 *
 * 后端 record 字段以 camelCase 序列化输出（`@JsonAlias` 仅用于读入 snake_case，不影响出参），
 * 故此处字段名按 camelCase 与出参对齐。客户端只读，字段全部可空以容忍尚未建画像的用户。
 */
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

@Serializable
data class BaseLevelDto(
    val programmingYears: Int? = null,
    val languagesKnown: List<String> = emptyList(),
    val pythonSelfRatedLevel: Int? = null,
)

@Serializable
data class LearningGoalDto(
    val kind: String? = null,
    val deadlineIso: String? = null,
    val targetKcCodes: List<String> = emptyList(),
)

@Serializable
data class CognitiveStyleDto(
    val fieldDependent: Double? = null,
    val sequentialVsHolistic: Double? = null,
)

@Serializable
data class LearningPreferenceDto(
    val mediaPriority: List<String> = emptyList(),
    val sessionMinutesPerDay: Int? = null,
)

@Serializable
data class ErrorPatternsDto(
    val frequentKcCodes: List<String> = emptyList(),
    val commonCategories: List<String> = emptyList(),
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
