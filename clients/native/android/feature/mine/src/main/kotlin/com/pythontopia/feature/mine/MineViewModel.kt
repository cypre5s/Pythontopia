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

/** Bug 矿洞 ViewModel；判题以服务端为准，异常写入 [MineUiState.errorMessage]，不做本地 fallback。 */
@HiltViewModel
class MineViewModel @Inject constructor(
    private val repository: MineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MineUiState())
    val state: StateFlow<MineUiState> = _state.asStateFlow()

    /** 开副本：POST sessions → GET cards，首张卡 snippet 灌入编辑器。 */
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

    /** 回写玩家代码到 [MineUiState.userCode]。 */
    fun updateCode(code: String) {
        _state.update { it.copy(userCode = code) }
    }

    /** 提交修复代码；失败达 [MineUiState.ESCAPE_THRESHOLD] 次弹 [MineEscapeDialog]。 */
    fun submitAttempt() {
        _state.value.currentCard ?: return
        val session = _state.value.session ?: return
        if (_state.value.isLoading) return
        val codeNow = _state.value.userCode
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.attempt(session.sessionId, codeNow) }
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

    /** 切下一卡：先用本地 [MineUiState.cards]，耗尽则 [MineRepository.next]；null 表示通关。切卡重置失败计数与反馈态。 */
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

    /** 放弃本卡并 [nextCard]；Pythoco 扣减由后端 quest_evidence 处理。 */
    fun escapeCurrentCard() {
        _state.update { it.copy(showEscapeDialog = false) }
        nextCard()
    }

    /** 召唤 NPC 提示；当前仅置 [MineUiState.npcHintRevealed] 展示思路文案。 */
    fun summonNpcHint() {
        _state.update { it.copy(showEscapeDialog = false, npcHintRevealed = true) }
    }

    /** 关闭撤退对话框，保留 [MineUiState.failureCount]。 */
    fun dismissEscapeDialog() {
        _state.update { it.copy(showEscapeDialog = false) }
    }

    /** 清除 [MineUiState.errorMessage]。 */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
