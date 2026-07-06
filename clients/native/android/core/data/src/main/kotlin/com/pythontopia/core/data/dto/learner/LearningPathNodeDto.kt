package com.pythontopia.core.data.dto.learner

import kotlinx.serialization.Serializable

/** 学习路径节点，对应后端 `LearningPathNodeDto`。 */
@Serializable
data class LearningPathNodeDto(
    val id: Long,
    val pathId: Long? = null,
    val dayIndex: Int = 0,
    val orderInDay: Int = 0,
    val kcCode: String? = null,
    val nodeType: PathNodeType? = null,
    val reasonSummary: String? = null,
    val evidenceSummary: String? = null,
    val suggestedResourceType: SuggestedResourceType? = null,
    val suggestedProblemId: Long? = null,
    val sourceClassroomQuestId: Long? = null,
)

@Serializable
enum class PathNodeType { READ_RESOURCE, SOLVE_PROBLEM, FIX_BUG, PLANT_KC, PROFILE_UPDATE }

@Serializable
enum class SuggestedResourceType { HANDOUT, MINDMAP, PRACTICE, CODE_LAB, READING, STORYBOARD }
