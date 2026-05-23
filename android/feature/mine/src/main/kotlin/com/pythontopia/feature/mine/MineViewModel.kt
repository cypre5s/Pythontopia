package com.pythontopia.feature.mine

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
 * Bug 矿洞副本 ViewModel：编排「开副本 → 选卡 → 写代码 → 提交 → 反馈 → 下一卡 / 撤退」全流程。
 *
 * 对齐 `docs/gameplay/r12-deepen/04-bugmine-puzzle-deck.md` §8.3 状态机：
 *  - [startSession] 进入 ENTRY → CARD_PICK
 *  - [updateCode] / [submitAttempt] 走 CARD_SOLVE → SUBMIT → PASS/FAIL
 *  - [nextCard] 走 PASS → NEXT_OR_EXIT
 *  - [escapeCurrentCard] / [summonNpcHint] 走 FAIL(≥3) → ESCAPE/HELP
 *
 * 仓库异常都 fold 到 [MineUiState.errorMessage]；不在 ViewModel 层走 demo fallback，因为
 * 副本判题必须以服务端为权威（AGENTS.md「Pyodide 不能当正式判题结果」），fallback 出来的
 * "通过" 会污染 quest_evidence。
 */
@HiltViewModel
class MineViewModel @Inject constructor(
    private val repository: MineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MineUiState())
    val state: StateFlow<MineUiState> = _state.asStateFlow()

    /**
     * 开新副本。
     *
     * 入口三类：失败提交触发（传 [srcSubmissionId]）/ 教师任务 / 主动 hub 入口。
     * 流程：POST sessions → 立刻 GET cards，把首张卡灌进编辑器；任一步失败展示 errorMessage。
     */
    fun startSession(srcSubmissionId: Long? = null) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                val session = repository.startSession(srcSubmissionId)
                val cards = repository.cards(session.sessionId)
                session to cards
            }
            result.fold(
                onSuccess = { (session, cards) ->
                    val first = cards.firstOrNull()
                    _state.update {
                        MineUiState(
                            isLoading = false,
                            session = session,
                            cards = cards,
                            cardIdx = 0,
                            userCode = first?.snippet.orEmpty(),
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "副本加载失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }

    /** 玩家编辑代码时回写到 [MineUiState.userCode]；只读字段，仓库不参与。 */
    fun updateCode(code: String) {
        _state.update { it.copy(userCode = code) }
    }

    /**
     * 提交当前卡的修复代码。
     *
     * PASS：清空 failureCount 与 lastResult.message 替换为成功提示；UI 展示「下一卡」按钮。
     * FAIL/RUNTIME_ERROR/TIMEOUT：failureCount + 1，达到 [MineUiState.ESCAPE_THRESHOLD]
     *   自动弹 [MineEscapeDialog]，让玩家选择撤退或召唤 NPC 提示。
     */
    fun submitAttempt() {
        val card = _state.value.currentCard ?: return
        if (_state.value.isLoading) return
        val codeNow = _state.value.userCode
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.attempt(card.id, codeNow) }
            result.fold(
                onSuccess = { attempt ->
                    val passed = attempt.status == MineAttempt.STATUS_PASS
                    val newFailureCount = if (passed) 0 else _state.value.failureCount + 1
                    _state.update {
                        it.copy(
                            isLoading = false,
                            lastResult = attempt,
                            failureCount = newFailureCount,
                            showEscapeDialog = !passed && newFailureCount >= MineUiState.ESCAPE_THRESHOLD,
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "提交失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }

    /**
     * 切到下一张卡。
     *
     * 优先用本地 [MineUiState.cards] 列表里的下一张（首屏已发的候补）；
     * 列表耗尽则向后端请求新卡 [MineRepository.next]，null 表示副本结束。
     *
     * 切卡同时清空 [MineUiState.failureCount] / [MineUiState.lastResult] /
     * [MineUiState.showEscapeDialog] / [MineUiState.npcHintRevealed]，让新卡是个干净开局。
     */
    fun nextCard() {
        val session = _state.value.session ?: return
        if (_state.value.isLoading) return
        val nextIdx = _state.value.cardIdx + 1
        val localNext = _state.value.cards.getOrNull(nextIdx)
        if (localNext != null) {
            _state.update {
                it.copy(
                    cardIdx = nextIdx,
                    userCode = localNext.snippet,
                    lastResult = null,
                    failureCount = 0,
                    showEscapeDialog = false,
                    npcHintRevealed = false,
                )
            }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.next(session.sessionId) }
            result.fold(
                onSuccess = { newCard ->
                    if (newCard == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isCompleted = true,
                                showEscapeDialog = false,
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                cards = it.cards + newCard,
                                cardIdx = nextIdx,
                                userCode = newCard.snippet,
                                lastResult = null,
                                failureCount = 0,
                                showEscapeDialog = false,
                                npcHintRevealed = false,
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "拉取下一卡失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }

    /**
     * 玩家在 ESCAPE 对话框选择「放弃本卡」。
     *
     * 直接复用 [nextCard] 流程跳到下一张；扣分（-2 Pythoco）的副作用由后端 quest_evidence
     * 统一处理，前端不预扣，避免双写。
     */
    fun escapeCurrentCard() {
        _state.update { it.copy(showEscapeDialog = false) }
        nextCard()
    }

    /**
     * 玩家在 ESCAPE 对话框选择「召唤同学 NPC」。
     *
     * MVP 阶段只在前端打标 [MineUiState.npcHintRevealed]，UI 端展示一段思路提示文本；
     * R-13 接 `student_diligent` 真实对话树后，这里改为派发 `DialogIntent.openFor` 跳 NPC 对话。
     */
    fun summonNpcHint() {
        _state.update { it.copy(showEscapeDialog = false, npcHintRevealed = true) }
    }

    /** 让玩家选择「再试一次」时关闭对话框、保留失败计数（不归零，避免无限躲过门槛）。 */
    fun dismissEscapeDialog() {
        _state.update { it.copy(showEscapeDialog = false) }
    }

    /** 玩家手动消解错误提示。 */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
