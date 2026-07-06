package com.pythontopia.feature.classroom

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 班级聚合 DTO；对应后端 `ClassroomDto`。 */
@Serializable
data class Classroom(
    val classroomId: Long,
    val name: String,
    val description: String? = null,
    val teacherId: Long,
    val packId: Long,
    val status: String,
    val joinCode: String,
)

/** 班级成员；对应后端 `ClassroomMemberDto`。 */
@Serializable
data class ClassroomMember(
    val studentId: Long,
    val username: String,
    val displayName: String? = null,
    val status: String,
)

/**
 * 班级任务；提交 evidence 用 [studentQuestId]（`/api/classroom/quests/mine`），
 * 非 [id]（教师维度任务 ID）。
 */
@Serializable
data class ClassroomQuest(
    val id: Long,
    val classroomId: Long,
    val title: String,
    val description: String? = null,
    val kcCodes: List<String> = emptyList(),
    val nodeType: String,
    val dueDate: String,
    val status: String,
    val targetProblemId: Long? = null,
    val targetResourceType: String? = null,
    val studentQuestId: Long? = null,
    val studentQuestStatus: String? = null,
    val requiredEvidenceType: String? = null,
)

/** KC 热力图单格；[avgMastery] 为 BigDecimal 字符串透传，由 [KcHeatmapView] 解析着色。 */
@Serializable
data class KcHeatmapEntry(
    val kcCode: String,
    val kcName: String,
    val chapterId: Long? = null,
    val chapterOrder: Int? = null,
    val avgMastery: String,
    val studentCount: Int,
    val masteredCount: Int,
    val learningCount: Int,
    val strugglingCount: Int,
)

/** 高频误区聚类；对应后端 `ErrorHotspotDto`。 */
@Serializable
data class ErrorHotspot(
    val kcCode: String,
    val kcName: String,
    val failedSubmissionCount: Int,
    val affectedStudentCount: Int,
    val categories: List<ErrorCategory> = emptyList(),
) {
    /** 错误分类条目。 */
    @Serializable
    data class ErrorCategory(
        val category: String,
        val count: Int,
    )
}

/**
 * 任务提交 evidence；字段必填规则对齐后端 `QuestEvidenceVerifier` 与 [ClassroomQuest.requiredEvidenceType]。
 */
@Serializable
data class QuestEvidence(
    val note: String? = null,
    val submissionId: Long? = null,
    val resourceId: Long? = null,
    val pageReadId: Long? = null,
    @SerialName("kcMasteryDelta") val kcMasteryDelta: Double? = null,
) {
    /** 仅 note、无结构化 evidence 的兼容构造。 */
    constructor(note: String?) : this(note = note, submissionId = null)
}
