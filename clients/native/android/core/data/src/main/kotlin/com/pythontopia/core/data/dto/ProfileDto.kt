package com.pythontopia.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val userId: Long,
    val major: String? = null,
    val knowledgeBase: String? = null,
    val learningGoal: String? = null,
    val cognitiveStyle: String? = null,
    val learningPreference: String? = null,
    val errorPatterns: String? = null,
    val practiceAbility: String? = null,
    val persistence: String? = null,
)
