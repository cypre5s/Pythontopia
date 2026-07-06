package com.pythontopia.feature.arena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 训练场 ViewModel：加载赛事、选题、编辑、提交、刷新积分榜。 */
@HiltViewModel
class ArenaViewModel @Inject constructor(
    private val repository: ArenaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ArenaUiState())
    val state: StateFlow<ArenaUiState> = _state.asStateFlow()

    init {
        refreshSession()
        refreshLeaderboard()
    }

    /** 切到积分榜且本地为空时懒加载；已有数据不重复请求。 */
    fun selectTab(tab: ArenaTab) {
        _state.update { it.copy(selectedTab = tab, errorMessage = null) }
        if (tab == ArenaTab.LEADERBOARD && state.value.leaderboard.isEmpty()) {
            refreshLeaderboard()
        }
    }

    /** 越界索引静默忽略。 */
    fun selectProblem(idx: Int) {
        val problems = state.value.session?.problems ?: return
        if (idx !in problems.indices) return
        _state.update { it.copy(currentProblemIdx = idx, errorMessage = null) }
    }

    /** session 未加载时静默丢弃。 */
    fun updateCode(newCode: String) {
        val problemId = state.value.currentProblem?.problemId ?: return
        _state.update {
            it.copy(codeBuffers = it.codeBuffers + (problemId to newCode))
        }
    }

    /** 仅 PASSED 时刷新积分榜；失败/运行时错误不刷新。 */
    fun submitCurrent() {
        val problem = state.value.currentProblem ?: return
        if (state.value.isSubmitting) return
        val code = state.value.currentCode
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.submit(problem.problemId, code) }
            result.fold(
                onSuccess = { submission ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            lastSubmission = submission,
                        )
                    }
                    if (submission.status == ArenaRepository.STATUS_PASSED) {
                        refreshLeaderboard()
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "提交失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }

    /** 刷新赛事；保留 [ArenaUiState.codeBuffers]。 */
    fun refreshSession() {
        _state.update { it.copy(isLoadingSession = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.todaysSession() }
            result.fold(
                onSuccess = { session ->
                    _state.update {
                        it.copy(
                            isLoadingSession = false,
                            session = session,
                            currentProblemIdx = it.currentProblemIdx.coerceAtMost(
                                (session.problems.size - 1).coerceAtLeast(0),
                            ),
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoadingSession = false,
                            errorMessage = "今日赛事加载失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }

    /** 拉取 local-only 个人战绩积分榜。 */
    fun refreshLeaderboard() {
        _state.update { it.copy(isLoadingLeaderboard = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.leaderboard() }
            result.fold(
                onSuccess = { list ->
                    _state.update {
                        it.copy(isLoadingLeaderboard = false, leaderboard = list)
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoadingLeaderboard = false,
                            errorMessage = "排行榜加载失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }
}
