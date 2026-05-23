package com.pythontopia.feature.classroom

import com.pythontopia.core.common.learner.StudentRole

/**
 * `ClassroomScreen` 的统一状态根。
 *
 * 同一个 [ClassroomScreen] 既要服务学生视角（"我的班级 / 公告板"）也要服务教师视角
 * （"我的班级 / 教师看板"），但**两边能展示的数据形态不同**：学生关心"哪些任务我要交
 * evidence"，教师关心"班级整体 KC 掌握分布 + 任务发布统计"。如果用单一扁平 data class
 * 持有全部字段，会带来三个问题：
 *  - 大量字段在某一视角下恒为空，难以判断"是真没数据还是属于另一视角"；
 *  - 教师才能看的 KC 热力图（[TeacherState.heatmap]）放在学生 state 里有越权风险；
 *  - 单元测试断言要写一长串 `state.heatmap.isEmpty() && state.quests.isEmpty() ...`。
 *
 * 因此用 [sealed interface] 切两个分支：[StudentState] / [TeacherState]，外加 [Loading]
 * 与 [Failure] 两个公共起手态。ViewModel 在登录时根据 [StudentRole] 决定走哪个分支并
 * 一直保持，不在同一会话内来回切。
 */
sealed interface ClassroomUiState {

    /**
     * 初始 / 加载中态。
     *
     * - 用户刚进入页面、登录态恢复前显示 loading 占位；
     * - 学生切换"我的班级" tab 选中不同班、教师重新拉热力图也短暂回到 Loading；
     * - [message] 可为 null（纯转圈）或带文案（"正在加载班级…"）让用户知道在做什么。
     */
    data class Loading(val message: String? = null) : ClassroomUiState

    /**
     * 失败兜底态。
     *
     * 用于 [ClassroomRepository.join] 等"非 fallback"方法抛异常时的展示。
     * 仓库内对 `myClassrooms()` 已经做了 `getOrElse { emptyList() }`，所以一般不会到 Failure；
     * 真正会触达此 state 的场景是：
     *  - join 拿到 404（班级不存在）
     *  - submitEvidence 拿到 422（evidence 不合法）
     *  - 首次打开 App 完全没有登录态
     *
     * @property code 后端错误码或本地分类（"NETWORK_OFFLINE" / "INVALID_JOIN_CODE" 等），
     *           用于 [ClassroomViewModel] 决定是否提示"重试" / "重输邀请码"。
     * @property message 给用户看的可读文案。
     */
    data class Failure(val code: String, val message: String) : ClassroomUiState

    /**
     * 学生视角主状态。
     *
     * @property classrooms 我加入的全部班级，按 `joinedAt` 倒序由后端排好；为空 list
     *           时 UI 展示"还没加入任何班级 → 输邀请码"引导。
     * @property selectedClassroomId 当前选中的班级 ID；null 表示"还没选"，此时公告板 tab
     *           为空。UI 默认选中 [classrooms].first()。
     * @property quests **当前选中班级**的任务列表（按 [ClassroomQuest.dueDate] 升序）。
     *           注意这里不是"全部班级的合集"，避免学生混淆"这个任务属于哪个班"。
     * @property pendingJoin 是否正在调用 `POST /api/classrooms/join`；用于 [JoinClassroomDialog]
     *           的提交按钮禁用 + 防抖。
     * @property submittingQuestId 正在提交 evidence 的 quest ID；该 quest 卡片显示
     *           loading，其他卡片保持可点击。null 表示当前没有提交在飞。
     * @property message 一次性提示文案（如"加入成功"），由 UI 消费后 ViewModel 清空。
     */
    data class StudentState(
        val classrooms: List<Classroom> = emptyList(),
        val selectedClassroomId: Long? = null,
        val quests: List<ClassroomQuest> = emptyList(),
        val pendingJoin: Boolean = false,
        val submittingQuestId: Long? = null,
        val message: String? = null,
    ) : ClassroomUiState {

        /** 便捷访问：当前选中的班级对象，若 [selectedClassroomId] 不在 [classrooms] 内返回 null。 */
        val selectedClassroom: Classroom?
            get() = classrooms.firstOrNull { it.classroomId == selectedClassroomId }
    }

    /**
     * 教师视角主状态。
     *
     * @property classrooms 我创建的班级；通常 1~3 个。
     * @property selectedClassroomId 当前关注的班级；UI 默认选 first()。
     * @property quests **当前选中班级**的全部任务（含 ARCHIVED），按 createdAt 倒序。
     * @property heatmap 65 条 KC 热力图数据；空 list 表示还在加载或后端无数据。
     * @property errorHotspots 高频误区聚类，按 [ErrorHotspot.failedSubmissionCount] 降序。
     * @property message 教师操作反馈（"任务已发布给 38 名学生"），UI 消费后清空。
     */
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

        /**
         * 工厂：按 [role] 返回对应分支的空初始态。
         *
         * 让 ViewModel `init {}` 块只写一行：
         * `_state = MutableStateFlow(ClassroomUiState.initial(role))`，
         * 无需到处分支判断。`ADMIN` 暂时按教师视角处理（教务可看全部 dashboard），
         * 真要区分时再扩展。
         */
        fun initial(role: StudentRole): ClassroomUiState =
            when (role) {
                StudentRole.STUDENT -> StudentState()
                StudentRole.TEACHER, StudentRole.ADMIN -> TeacherState()
            }
    }
}
