package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

/** 已解锁技能，对应后端 `LearnerSkillDto`。 */
@Serializable
data class LearnerSkillDto(
    val skillKey: String,
    val unlockedAt: String? = null,
    val proficiency: Int = 0,
)
