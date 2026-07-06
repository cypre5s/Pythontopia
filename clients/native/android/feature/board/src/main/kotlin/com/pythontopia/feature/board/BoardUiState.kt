package com.pythontopia.feature.board

import com.pythontopia.core.data.dto.learner.QuestDto
import com.pythontopia.core.data.dto.learner.QuestStatus

/** 公告板今日任务状态。完成/跳过/开始进行中的任务以 [actionInFlightId] 标记，避免重复点。 */
data class BoardUiState(
    val quests: List<QuestDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val actionInFlightId: Long? = null,
    val notice: String? = null,
) {
    /** 未完成优先、按优先级降序展示；已完成/跳过沉底。 */
    val sorted: List<QuestDto>
        get() = quests.sortedWith(
            compareBy<QuestDto> { statusRank(it.status) }.thenByDescending { it.priority }.thenBy { it.id },
        )

    val openCount: Int
        get() = quests.count { it.status == QuestStatus.OPEN || it.status == QuestStatus.IN_PROGRESS }

    private fun statusRank(status: QuestStatus?): Int = when (status) {
        QuestStatus.IN_PROGRESS -> 0
        QuestStatus.OPEN -> 1
        QuestStatus.COMPLETED -> 2
        QuestStatus.SKIPPED -> 3
        QuestStatus.EXPIRED -> 4
        null -> 5
    }
}

/** 需 evidence 的任务不能在公告板「无证据完成」。 */
fun QuestDto.requiresEvidence(): Boolean = !requiredEvidenceType.isNullOrBlank()

fun QuestDto.isActionable(): Boolean =
    status == QuestStatus.OPEN || status == QuestStatus.IN_PROGRESS
