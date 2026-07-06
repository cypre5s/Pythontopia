package com.pythontopia.feature.teacherreview

/** 教师审题状态：出题工具条（章/阶段）+ 本地审核队列。 */
data class TeacherReviewUiState(
    val chapter: Int = 1,
    val stage: Int = 1,
    val entries: List<ReviewEntry> = emptyList(),
    val selectedKey: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val selected: ReviewEntry? get() = entries.firstOrNull { it.key == selectedKey }
    val total: Int get() = entries.size
    val approved: Int get() = entries.count { it.decision == ReviewDecision.APPROVED }
    val rejected: Int get() = entries.count { it.decision == ReviewDecision.REJECTED }
    val pending: Int get() = entries.count { it.decision == ReviewDecision.PENDING }
}
