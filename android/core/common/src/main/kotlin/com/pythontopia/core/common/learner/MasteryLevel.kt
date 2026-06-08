package com.pythontopia.core.common.learner

/** 掌握度 [score] ∈ [0.0, 1.0]；[bucket] 按阈值映射为 novice / intermediate / proficient / mastery。 */
data class MasteryLevel(val score: Double) {
    init {
        require(score in 0.0..1.0) { "MasteryLevel score must be in [0.0, 1.0], got $score" }
    }

    val bucket: String
        get() = when {
            score < 0.3 -> "novice"
            score < 0.6 -> "intermediate"
            score < 0.85 -> "proficient"
            else -> "mastery"
        }
}
