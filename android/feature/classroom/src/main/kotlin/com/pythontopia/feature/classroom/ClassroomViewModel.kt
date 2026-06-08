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
 * 班级 ViewModel：学生与教师共用；[bind] 注入 [StudentRole] 后拉取数据，
 * 教师 dashboard 并发拉 quests/heatmap/errorHotspots。
 */
@HiltViewModel
class ClassroomViewModel @Inject constructor(
    private val repository: ClassroomRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ClassroomUiState>(ClassroomUiState.Loading())
    val state: StateFlow<ClassroomUiState> = _state.asStateFlow()

    /** 首次 [bind] 后固化；中途换 role 须重建 ViewModel。 */
    private var role: StudentRole? = null

    /** 注入角色并触发数据拉取；相同 role 重复调用视为刷新。 */
    fun bind(role: StudentRole) {
        val firstBind = this.role == null
        if (this.role != role) {
            _state.value = ClassroomUiState.initial(role)
        }
        this.role = role
        viewModelScope.launch { refresh(initial = firstBind) }
    }

    /** 切换当前班级并拉取该班详情；classroomId 不在列表内则忽略。 */
    fun selectClassroom(classroomId: Long) {
        val currentRole = role ?: return
        viewModelScope.launch {
            when (currentRole) {
                StudentRole.STUDENT -> updateStudent { it.copy(selectedClassroomId = classroomId) }
                StudentRole.TEACHER, StudentRole.ADMIN ->
                    updateTeacher { it.copy(selectedClassroomId = classroomId) }
            }
            runCatching { loadClassroomDetail(classroomId) }
                .onFailure { error ->
                    _state.value = ClassroomUiState.Failure(
                        code = "CLASSROOM_DETAIL_FAILED",
                        message = error.message ?: "班级详情加载失败，请稍后重试",
                    )
                }
        }
    }

    /** 学生用邀请码加入；[joinCode] 须 6 位。 */
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
                    runCatching { loadClassroomDetail(joined.classroomId) }
                        .onFailure { error ->
                            _state.value = ClassroomUiState.Failure(
                                code = "CLASSROOM_DETAIL_FAILED",
                                message = error.message ?: "班级详情加载失败，请稍后重试",
                            )
                        }
                }
                .onFailure { error ->
                    _state.value = ClassroomUiState.Failure(
                        code = "JOIN_FAILED",
                        message = error.message ?: "加入失败，请检查邀请码后重试",
                    )
                }
        }
    }

    /** 提交任务 evidence；[ClassroomQuest.studentQuestId] 为空时拒绝。 */
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

    /** 清除一次性 [ClassroomUiState.StudentState.message] / [ClassroomUiState.TeacherState.message]。 */
    fun consumeMessage() {
        when (val current = _state.value) {
            is ClassroomUiState.StudentState -> _state.value = current.copy(message = null)
            is ClassroomUiState.TeacherState -> _state.value = current.copy(message = null)
            else -> Unit
        }
    }

    /** Failure 对话框关闭后复位并重新拉取。 */
    fun dismissFailure() {
        val currentRole = role ?: return
        if (_state.value is ClassroomUiState.Failure) {
            _state.value = ClassroomUiState.initial(currentRole)
            viewModelScope.launch { refresh(initial = false) }
        }
    }

    private suspend fun refresh(initial: Boolean) {
        if (initial) _state.value = ClassroomUiState.Loading("正在加载班级…")
        runCatching {
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
        }.onFailure { error ->
            _state.value = ClassroomUiState.Failure(
                code = "CLASSROOM_LOAD_FAILED",
                message = error.message ?: "班级加载失败，请确认已登录且后端可用",
            )
        }
    }

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
        /** 邀请码 6 位；服务端 `^[A-Z0-9]{6}$`。 */
        const val JOIN_CODE_LENGTH = 6
    }
}
