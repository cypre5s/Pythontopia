package com.pythontopia.core.data.dto.learner

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 公告板/路径任务，对应后端 `QuestDto`。不可变 JSONB（payload/deepLinkParams）保留为 [JsonElement]。 */
@Serializable
data class QuestDto(
    val id: Long,
    val userId: Long? = null,
    val source: QuestSource? = null,
    val sourceRefId: Long? = null,
    val title: String,
    val description: String? = null,
    val kcCode: String? = null,
    val nodeType: QuestNodeType? = null,
    val payload: JsonElement? = null,
    val dueDate: String? = null,
    val priority: Int = 0,
    val status: QuestStatus? = null,
    val requiredEvidenceType: String? = null,
    val requiredMasteryDelta: Double? = null,
    val targetProblemIds: List<Long> = emptyList(),
    val targetResourceId: Long? = null,
    val deepLinkRoute: String? = null,
    val deepLinkParams: JsonElement? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
enum class QuestSource { PATH_PLANNER, CLASSROOM, SYSTEM_DEFAULT }

@Serializable
enum class QuestStatus { OPEN, IN_PROGRESS, COMPLETED, SKIPPED, EXPIRED }

@Serializable
enum class QuestNodeType { READ_RESOURCE, SOLVE_PROBLEM, FIX_BUG, PLANT_KC, PROFILE_UPDATE }
