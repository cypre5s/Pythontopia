package com.pythontopia.feature.classroom

import com.pythontopia.core.common.learner.StudentRole

/**
 * [ClassroomScreen] 状态根：按 [StudentRole] 分 [StudentState]/[TeacherState]，
 * 避免学生可见教师专属字段（如 heatmap）。
 */
sealed interface ClassroomUiState {

    /** 加载中。 */
    data class Loading(val message: String? = null) : ClassroomUiState

    /** 操作失败；[join]/[ClassroomViewModel.submitEvidence] 等会进入此态。 */
    data class Failure(val code: String, val message: String) : ClassroomUiState

    /**
     * 学生视角；[quests] 为当前选中班级的任务，
     * [submittingQuestId] 标记正在提交 evidence 的 quest。
     */
    data class StudentState(
        val classrooms: List<Classroom> = emptyList(),
        val selectedClassroomId: Long? = null,
        val quests: List<ClassroomQuest> = emptyList(),
        val pendingJoin: Boolean = false,
        val submittingQuestId: Long? = null,
        val message: String? = null,
    ) : ClassroomUiState {

        val selectedClassroom: Classroom?
            get() = classrooms.firstOrNull { it.classroomId == selectedClassroomId }
    }

    /** 教师视角；含 KC 热力图与高频误区。 */
    data class TeacherState(
        val classrooms: List<Classroom> = emptyList(),
        val selectedClassroomId: Long? = null,
        val quests: List<ClassroomQuest> = emptyList(),
        val heatmap: List<KcHeatmapEntry> = emptyList(),
        val errorHotspots: List<ErrorHotspot> = emptyList(),
        val message: String? = null,
    ) : ClassroomUiState {

        val selectedClassroom: Classroom?
            get() = classrooms.firstOrNull { it.classroomId == selectedClassroomId }
    }

    companion object {

        /** 按 [role] 返回对应分支的空初始态；ADMIN 按教师处理。 */
        fun initial(role: StudentRole): ClassroomUiState =
            when (role) {
                StudentRole.STUDENT -> StudentState()
                StudentRole.TEACHER, StudentRole.ADMIN -> TeacherState()
            }
    }
}
