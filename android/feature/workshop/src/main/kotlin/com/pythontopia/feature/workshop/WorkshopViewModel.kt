package com.pythontopia.feature.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Workshop（代码工坊）ViewModel：协调 [WorkshopRepository] 与 [WorkshopUiState]。
 *
 * 关键流程：
 *  1. [openPicker] / [loadProblems]：按 [WorkshopUiState.kcCodeFilter] 拉题目列表；
 *  2. [selectProblem]：把题目装入状态并重置代码编辑器（同题保留代码）；
 *  3. [updateCode]：纯本地状态写入，不触发网络；
 *  4. [submit]：先 POST 拿到初始 [SubmissionDto]，再启动轮询 [Job] 跟新 latestStatus，
 *     终态后自动刷新 [WorkshopUiState.history]；
 *  5. 用户连续 [submit] 会先 cancel 已有的轮询 Job，避免两个 polling 串扰。
 *
 * 历史归属：服务端 `/api/submissions` 走 CurrentUser 鉴权，Android 不再传 demo userId，
 * 避免把本地占位身份冒充正式业务数据。
 */
@HiltViewModel
class WorkshopViewModel @Inject constructor(
    private val repository: WorkshopRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkshopUiState())
    val state: StateFlow<WorkshopUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    fun openPicker() {
        _state.update { it.copy(isPickerOpen = true, errorMessage = null) }
        loadProblems()
    }

    fun dismissPicker() {
        _state.update { it.copy(isPickerOpen = false) }
    }

    fun changeKcFilter(kcCode: String) {
        if (kcCode == _state.value.kcCodeFilter) return
        _state.update { it.copy(kcCodeFilter = kcCode, problems = emptyList()) }
        if (_state.value.isPickerOpen) {
            loadProblems()
        }
    }

    fun loadProblems() {
        viewModelScope.launch {
            val kcCode = _state.value.kcCodeFilter
            _state.update { it.copy(isLoadingProblems = true, errorMessage = null) }
            val problems = repository.problems(kcCode)
            _state.update { current ->
                current.copy(
                    isLoadingProblems = false,
                    problems = problems,
                    errorMessage = if (problems.isEmpty()) {
                        "未拉到 $kcCode 的题目，请确认后端 /api/course/problems 已上线"
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun selectProblem(problem: ProblemDto) {
        _state.update { current ->
            val keepCode = current.currentProblem?.id == problem.id && current.userCode.isNotBlank()
            current.copy(
                currentProblem = problem,
                userCode = if (keepCode) current.userCode else WorkshopUiState.DEFAULT_STARTER_CODE,
                isPickerOpen = false,
                latestStatus = null,
                currentSubmissionId = null,
                errorMessage = null,
            )
        }
    }

    fun updateCode(text: String) {
        _state.update { it.copy(userCode = text) }
    }

    fun submit() {
        val snapshot = _state.value
        val problem = snapshot.currentProblem ?: return
        if (!snapshot.canSubmit) return

        pollingJob?.cancel()
        _state.update {
            it.copy(
                isSubmitting = true,
                latestStatus = null,
                currentSubmissionId = null,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            val initial = runCatching { repository.submit(problem.id, snapshot.userCode) }
            initial.onFailure { error ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = "提交失败：${error.message ?: "网络异常"}",
                    )
                }
                return@launch
            }
            val firstStatus = initial.getOrThrow()
            _state.update {
                it.copy(
                    isSubmitting = false,
                    currentSubmissionId = firstStatus.id,
                    latestStatus = firstStatus,
                    isPolling = !firstStatus.isTerminal,
                )
            }
            if (!firstStatus.isTerminal) {
                startPolling(firstStatus.id)
            } else {
                loadHistory()
            }
        }
    }

    private fun startPolling(submissionId: Long) {
        pollingJob = viewModelScope.launch {
            repository.pollStatus(submissionId)
                .onEach { dto ->
                    _state.update { it.copy(latestStatus = dto, isPolling = !dto.isTerminal) }
                }
                .catch { error ->
                    _state.update {
                        it.copy(
                            isPolling = false,
                            errorMessage = "轮询失败：${error.message ?: "网络异常"}",
                        )
                    }
                }
                .onCompletion { cause ->
                    _state.update { it.copy(isPolling = false) }
                    val terminal = _state.value.latestStatus
                    if (cause == null && terminal != null && terminal.isTerminal) {
                        loadHistory()
                    }
                }
                .collect()
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            val items = repository.history()
            _state.update { it.copy(history = items) }
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

}
