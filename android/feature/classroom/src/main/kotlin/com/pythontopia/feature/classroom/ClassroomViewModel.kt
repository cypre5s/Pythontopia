package com.pythontopia.feature.classroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythontopia.core.common.learner.StudentRole
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * `:feature:classroom` 的 ViewModel：把 [ClassroomRepository] 封装为 [ClassroomUiState]，
 * 兼顾学生与教师两套交互。
 *
 * **角色判定**：构造时不知道 role，必须由 UI 在拿到登录态后调用 [bind] 注入 [StudentRole]。
 * 这样做是因为 `:feature:classroom` 当前并未持有"当前登录用户"的依赖（避免循环依赖
 * `:feature:classroom` → `:core:network/AuthTokenHolder` → 已有的 `:feature:auth`
 * SessionRepository 等），把 role 当成"UI 注入参数"是 Beta 阶段最省事的方案。
 *
 * **并发**：教师视角 dashboard 需要同时拉 quests / heatmap / errorHotspots 三类数据，
 * 用 `coroutineScope { async { ... } }` 并发执行；学生视角仅一次 quests 拉取，串行即可。
 * 不引入显式 IO Dispatcher 注入（避免新增 Hilt qualifier 模块），靠 Retrofit `suspend`
 * 内部自动切到 OkHttp 工作线程；测试时 `Dispatchers.setMain(UnconfinedTestDispatcher())`
 * 让 `viewModelScope` 同步执行 mockk 桩。
 *
 * **测试友好**：构造函数只依赖 [ClassroomRepository]（mockk 默认可 mock final 类），
 * 单测 `ClassroomViewModel(mockk<ClassroomRepository>())` 即可，无需 Hilt 容器。
 */
@HiltViewModel
class ClassroomViewModel @Inject constructor(
    private val repository: ClassroomRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ClassroomUiState>(ClassroomUiState.Loading())
    val state: StateFlow<ClassroomUiState> = _state.asStateFlow()

    /** 当前会话的角色；首次 [bind] 后固化，不允许中途切换（切换 = 重建 ViewModel）。 */
    private var role: StudentRole? = null

    /**
     * UI 进入屏幕后调用：注入角色 + 触发首次数据拉取。
     *
     * 幂等：重复调用相同 [role] 视为下拉刷新，重新拉所有数据；调用不同 [role]
     * 会触发一次"角色变更"重置（[ClassroomUiState] 完全替换为对应分支的空态）。
     *
     * @param role 当前登录用户的角色（[StudentRole.STUDENT] / [StudentRole.TEACHER] / [StudentRole.ADMIN]）
     */
    fun bind(role: StudentRole) {
        val firstBind = this.role == null
        if (this.role != role) {
            _state.value = ClassroomUiState.initial(role)
        }
        this.role = role
        viewModelScope.launch { refresh(initial = firstBind) }
    }

    /**
     * 切换"当前关注的班级"。
     *
     * 学生视角：切换后重新拉该班级的活跃任务；
     * 教师视角：切换后并发拉该班级的 quests / heatmap / errorHotspots。
     *
     * @param classroomId 班级 ID；若不在 `state.classrooms` 内则忽略调用。
     */
    fun selectClassroom(classroomId: Long) {
        val currentRole = role ?: return
        viewModelScope.launch {
            when (currentRole) {
                StudentRole.STUDENT -> updateStudent { it.copy(selectedClassroomId = classroomId) }
                StudentRole.TEACHER, StudentRole.ADMIN ->
                    updateTeacher { it.copy(selectedClassroomId = classroomId) }
            }
            loadClassroomDetail(classroomId)
        }
    }

    /**
     * 学生加入新班级。
     *
     * 提交过程中 `pendingJoin = true`，UI 应禁用按钮防重复点击；
     * - 成功：班级追加到列表并自动选中，`message = "已加入：{name}"`；
     * - 失败：state 进入 [ClassroomUiState.Failure]（保留原 [ClassroomUiState.StudentState]
     *   到下次 `bind()` 才覆盖）。
     *
     * @param joinCode 6 位邀请码；UI 已做 `[A-Z0-9]{6}` 校验，但本方法仍把空串/
     *        长度异常作为 INVALID_JOIN_CODE 兜底。
     */
    fun join(joinCode: String) {
        val current = _state.value as? ClassroomUiState.StudentState ?: return
        if (joinCode.length != JOIN_CODE_LENGTH) {
            _state.value = ClassroomUiState.Failure("INVALID_JOIN_CODE", "邀请码必须是 6 位字符")
            return
        }
        _state.value = current.copy(pendingJoin = true)
        viewModelScope.launch {
            runCatching { repository.join(joinCode) }
                .onSuccess { joined ->
                    val base = (_state.value as? ClassroomUiState.StudentState) ?: current
                    _state.value = base.copy(
                        classrooms = current.classrooms + joined,
                        selectedClassroomId = joined.classroomId,
                        pendingJoin = false,
                        message = "已加入：${joined.name}",
                    )
                    loadClassroomDetail(joined.classroomId)
                }
                .onFailure { error ->
                    _state.value = ClassroomUiState.Failure(
                        code = "JOIN_FAILED",
                        message = error.message ?: "加入失败，请检查邀请码后重试",
                    )
                }
        }
    }

    /**
     * 学生提交某条班级任务的 evidence。
     *
     * 提交过程中 `submittingQuestId = quest.studentQuestId`，对应卡片显示 loading；
     * 提交成功后用后端返回的最新 quest 状态覆盖本地列表，并把 `message` 设为"已提交"；
     * 失败回到原 quest 列表（不删字段），把后端 `BusinessException.message` 透传到 [ClassroomUiState.Failure]。
     *
     * **前置校验**：[ClassroomQuest.studentQuestId] 必须非空（来自 `/api/classroom/quests/mine`
     * 的响应），否则提交无意义直接被本方法早返回 + Failure。
     */
    fun submitEvidence(quest: ClassroomQuest, evidence: QuestEvidence) {
        val current = _state.value as? ClassroomUiState.StudentState ?: return
        val studentQuestId = quest.studentQuestId
        if (studentQuestId == null) {
            _state.value = ClassroomUiState.Failure(
                code = "STUDENT_QUEST_MISSING",
                message = "当前任务未关联个人记录，无法提交",
            )
            return
        }
        _state.value = current.copy(submittingQuestId = studentQuestId)
        viewModelScope.launch {
            runCatching { repository.submitEvidence(studentQuestId, evidence) }
                .onSuccess { updated ->
                    val nextQuests = current.quests.map { existing ->
                        if (existing.id == quest.id) updated else existing
                    }
                    _state.value = current.copy(
                        quests = nextQuests,
                        submittingQuestId = null,
                        message = "已提交：${quest.title}",
                    )
                }
                .onFailure { error ->
                    _state.value = current.copy(submittingQuestId = null)
                    _state.value = ClassroomUiState.Failure(
                        code = "EVIDENCE_REJECTED",
                        message = error.message ?: "提交未通过，请检查证据后重试",
                    )
                }
        }
    }

    /**
     * UI 消费一次性 `message` 后调用，避免 toast 重复弹出。
     */
    fun consumeMessage() {
        when (val current = _state.value) {
            is ClassroomUiState.StudentState -> _state.value = current.copy(message = null)
            is ClassroomUiState.TeacherState -> _state.value = current.copy(message = null)
            else -> Unit
        }
    }

    /**
     * UI 在错误对话框 dismiss 时调用：把 [ClassroomUiState.Failure] 复位为对应角色的
     * 空态，让用户能继续重试操作（输入新邀请码 / 重选班级）。
     */
    fun dismissFailure() {
        val currentRole = role ?: return
        if (_state.value is ClassroomUiState.Failure) {
            _state.value = ClassroomUiState.initial(currentRole)
            viewModelScope.launch { refresh(initial = false) }
        }
    }

    /**
     * 拉取 `/api/classrooms/mine` 并按角色装配初始 state。
     *
     * @param initial true = 首次 bind（state 一定从 Loading 切到主分支）；
     *                false = 下拉刷新（保留 selectedClassroomId）。
     */
    private suspend fun refresh(initial: Boolean) {
        if (initial) _state.value = ClassroomUiState.Loading("正在加载班级…")
        val classrooms = repository.myClassrooms()
        val selectedId = classrooms.firstOrNull()?.classroomId
        when (role) {
            StudentRole.STUDENT ->
                _state.value = ClassroomUiState.StudentState(
                    classrooms = classrooms,
                    selectedClassroomId = selectedId,
                )
            StudentRole.TEACHER, StudentRole.ADMIN ->
                _state.value = ClassroomUiState.TeacherState(
                    classrooms = classrooms,
                    selectedClassroomId = selectedId,
                )
            null -> Unit
        }
        if (selectedId != null) loadClassroomDetail(selectedId)
    }

    /**
     * 学生：拉该班的活跃任务；
     * 教师：并发拉 quests / heatmap / errorHotspots（限并发为 3）。
     */
    private suspend fun loadClassroomDetail(classroomId: Long) {
        when (role) {
            StudentRole.STUDENT -> {
                val quests = repository.quests(classroomId, asTeacher = false)
                updateStudent { it.copy(quests = quests) }
            }
            StudentRole.TEACHER, StudentRole.ADMIN -> {
                val (quests, heatmap, hotspots) = coroutineScope {
                    val q = async { repository.quests(classroomId, asTeacher = true) }
                    val h = async { repository.heatmap(classroomId) }
                    val e = async { repository.errorHotspots(classroomId) }
                    Triple(q.await(), h.await(), e.await())
                }
                updateTeacher { it.copy(quests = quests, heatmap = heatmap, errorHotspots = hotspots) }
            }
            null -> Unit
        }
    }

    private inline fun updateStudent(
        crossinline transform: (ClassroomUiState.StudentState) -> ClassroomUiState.StudentState,
    ) {
        _state.update { current ->
            if (current is ClassroomUiState.StudentState) transform(current) else current
        }
    }

    private inline fun updateTeacher(
        crossinline transform: (ClassroomUiState.TeacherState) -> ClassroomUiState.TeacherState,
    ) {
        _state.update { current ->
            if (current is ClassroomUiState.TeacherState) transform(current) else current
        }
    }

    companion object {
        /** 邀请码固定 6 位字符；服务端 `@Pattern("^[A-Z0-9]{6}$")`。 */
        const val JOIN_CODE_LENGTH = 6
    }
}
