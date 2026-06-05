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

/**
 * 训练场 ViewModel：编排"加载今日赛事 → 选题 → 编辑代码 → 提交 → 刷新排行榜"五步闭环。
 *
 * 单状态对象 [ArenaUiState] + `MutableStateFlow` 暴露给 Compose。所有跨网络调用都进
 * [viewModelScope]，ViewModel 销毁时 Hilt 会取消协程，避免后台轮询泄露（这也是
 * AGENTS.md 中"viewModelScope.launch { while(isActive) }"约束的精神）。
 *
 * 与 hub / dialog 的 ViewModel 不同，此处暴露的对外 Intent 较多（5 个公开方法），刚好踩
 * 在"传统 MVVM 还撑得住，再加一个就要切 MVI"的边界上。Beta+1 接真实 PVP 时会拆出
 * `ArenaIntent` sealed class。
 */
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

    /**
     * 切换顶部 tab。
     *
     * 切到积分榜时如果本地数据为空，顺手再拉一次，避免"打开就空白"的尴尬演示。
     * 已有数据时不重复拉，给用户主动刷新留余地（[refreshLeaderboard] 是显式方法）。
     */
    fun selectTab(tab: ArenaTab) {
        _state.update { it.copy(selectedTab = tab, errorMessage = null) }
        if (tab == ArenaTab.LEADERBOARD && state.value.leaderboard.isEmpty()) {
            refreshLeaderboard()
        }
    }

    /**
     * 选中题目（按列表索引）。
     *
     * 越界静默忽略（不抛异常 / 不发错误，因为这是 UI bug 而非业务错误）；ProblemRunner 会
     * 自然显示"暂无题目"占位。
     */
    fun selectProblem(idx: Int) {
        val problems = state.value.session?.problems ?: return
        if (idx !in problems.indices) return
        _state.update { it.copy(currentProblemIdx = idx, errorMessage = null) }
    }

    /**
     * 玩家编辑代码时回写到 [ArenaUiState.codeBuffers]。
     *
     * 没有 problemId 上下文时（session 未加载）静默丢弃，避免空指针。
     */
    fun updateCode(newCode: String) {
        val problemId = state.value.currentProblem?.problemId ?: return
        _state.update {
            it.copy(codeBuffers = it.codeBuffers + (problemId to newCode))
        }
    }

    /**
     * 提交当前题目代码。
     *
     * 流程：
     *  1. 锁 [ArenaUiState.isSubmitting] 防重复点击
     *  2. 调 [ArenaRepository.submit] 拿三类终态之一
     *  3. 写入 [ArenaUiState.lastSubmission]
     *  4. 通过时顺手 [refreshLeaderboard]，让玩家立刻看到自己的排名变化
     *
     * 注意提交失败 / 运行时错误不刷新排行榜，因为这两种情况积分通常为 0，不会改变排名。
     */
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

    /**
     * 重新拉取今日赛事；保留玩家已编辑的 [ArenaUiState.codeBuffers]，避免误清空。
     */
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

    /**
     * 重新拉取“积分榜”。
     *
     * 后端没有跨用户 / 班级排行榜端点，仓库只能用当前账号自己的 PASSED 提交派生单行个人战绩
     * （local-only）。Beta+1 若后端补真实排行榜端点，再在此切换数据源。
     */
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
