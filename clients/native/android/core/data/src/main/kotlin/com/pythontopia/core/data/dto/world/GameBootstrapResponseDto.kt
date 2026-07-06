package com.pythontopia.core.data.dto.world

import com.pythontopia.core.data.dto.learner.LearnerProfileDto
import com.pythontopia.core.data.dto.learner.LearningPathNodeDto
import com.pythontopia.core.data.dto.learner.MasterySnapshotDto
import com.pythontopia.core.data.dto.learner.QuestDto
import kotlinx.serialization.Serializable

/**
 * `POST /api/game/bootstrap` 聚合载荷，1:1 对应后端 `GameBootstrapResponse`；
 * 未声明字段由 kotlinx.serialization ignoreUnknownKeys 忽略。
 *
 * R-14 世界收缩后**没有**背包/货币/商店：历史虚构的 inventory/currency 字段已移除，
 * 补齐了真实承载教学闭环的 profile/quests/mastery/path/skills/activeSlot/pendingEvents
 * （命名以后端 record 为准，存档字段为 [activeSlot]）。详见 docs/04-backend-contract-drift.md。
 */
@Serializable
data class GameBootstrapResponseDto(
    val world: WorldSnapshotDto,
    val player: PlayerStateDto,
    val profile: LearnerProfileDto? = null,
    val quests: List<QuestDto> = emptyList(),
    val mastery: List<MasterySnapshotDto> = emptyList(),
    val path: List<LearningPathNodeDto> = emptyList(),
    val skills: List<LearnerSkillDto> = emptyList(),
    val npcStates: List<NpcDialogStateDto> = emptyList(),
    val activeSlot: SaveSlotDto? = null,
    val pendingEvents: List<WorldEventDto> = emptyList(),
)
