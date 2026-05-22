package com.pythontopia.core.common.learner

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
