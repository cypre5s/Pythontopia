package com.pythontopia.feature.feynman

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
 * 费曼反演：填 KC → 开会话（后端用真实错题抽误解）→ 用自己的话讲给 AI 学弟 →
 * 学弟产出可判题代码 → 真判题裁定。逐步对齐 Web `stores/feynman.ts`。
 */
@HiltViewModel
class FeynmanViewModel @Inject constructor(
    private val repository: FeynmanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FeynmanUiState())
    val state: StateFlow<FeynmanUiState> = _state.asStateFlow()

    fun onKcChange(value: String) {
        _state.update { it.copy(kcCode = value) }
    }

    /** 深链/外部入口预填 KC（不自动开会话，保持与 Web `?kc=` 行为一致）。 */
    fun prefillKc(kc: String?) {
        val trimmed = kc?.trim().orEmpty()
        if (trimmed.isEmpty() || _state.value.kcCode.isNotEmpty()) return
        _state.update { it.copy(kcCode = trimmed, activeKc = trimmed) }
    }

    fun openSession() {
        val kc = _state.value.kcCode.trim()
        if (kc.isEmpty()) return
        _state.update { it.copy(activeKc = kc) }
        startSession(kc)
    }

    fun startSession(kcCode: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.startSession(kcCode)
                .onSuccess { session ->
                    _state.update {
                        it.copy(
                            currentSession = session,
                            turns = emptyList(),
                            latestProducedCode = session.tuteeState.producedCode.orEmpty(),
                            latestJudgeResult = session.tuteeState.judgeResult,
                            loading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = "开启费曼会话失败：${e.message ?: "服务不可用"}")
                    }
                }
        }
    }

    fun teach(explanation: String) {
        val trimmed = explanation.trim()
        val sessionId = _state.value.currentSession?.id ?: return
        if (trimmed.isEmpty() || _state.value.teaching) return
        _state.update { it.copy(teaching = true, error = null) }
        viewModelScope.launch {
            repository.teach(sessionId, trimmed)
                .onSuccess { turn ->
                    _state.update { s ->
                        // 后端用 taughtFix 承载学弟产出代码；端侧 producedCode 优先。
                        val produced = turn.producedCode ?: turn.taughtFix
                        s.copy(
                            turns = s.turns + turn,
                            latestProducedCode = produced ?: s.latestProducedCode,
                            latestJudgeResult = turn.judgeResult ?: s.latestJudgeResult,
                            teaching = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(teaching = false, error = "学弟没听懂：${e.message ?: "服务不可用"}")
                    }
                }
        }
    }

    fun judge() {
        val s = _state.value
        val sessionId = s.currentSession?.id ?: return
        if (!s.canJudge) return
        _state.update { it.copy(judging = true, error = null) }
        viewModelScope.launch {
            repository.judge(sessionId)
                .onSuccess { verdict ->
                    val evidence = s.misconceptions.flatMap { it.evidenceEventIds }
                    val result = FeynmanJudgeResultDto(
                        status = if (verdict.passed) "PASSED" else "FAILED",
                        messageZh = verdict.message,
                        evidenceEventIds = evidence,
                    )
                    _state.update { it.copy(judging = false, latestJudgeResult = result) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(judging = false, error = "判题失败：${e.message ?: "判题服务不可用"}")
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // ── 代码找茬小游戏（移植 Web BugHunterGame） ──

    /** 拉取一局含 bug 代码样本并开局；保留 best/streak 跨局累计。 */
    fun startBugHunt() {
        val sessionId = _state.value.currentSession?.id ?: return
        if (_state.value.bugHunt.loading) return
        _state.update { it.copy(bugHunt = it.bugHunt.copy(loading = true)) }
        viewModelScope.launch {
            repository.bugHunt(sessionId)
                .onSuccess { dto ->
                    val lines = dto.buggyCode?.removeSuffix("\n")?.split("\n").orEmpty()
                    _state.update {
                        it.copy(
                            bugHunt = it.bugHunt.copy(
                                loading = false,
                                phase = if (lines.isNotEmpty()) BugHuntPhase.PLAYING else BugHuntPhase.IDLE,
                                lines = lines,
                                bugLines = dto.bugLineIndices.toSet(),
                                hintMd = dto.hintMd,
                                source = dto.source,
                                selected = emptySet(),
                                secondsLeft = BugHunterState.ROUND_SECONDS,
                                score = 0,
                                result = null,
                            ),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            bugHunt = it.bugHunt.copy(loading = false, phase = BugHuntPhase.IDLE),
                            error = "找茬样本生成失败：${e.message ?: "服务不可用"}",
                        )
                    }
                }
        }
    }

    fun toggleBugLine(index: Int) {
        val bh = _state.value.bugHunt
        if (bh.phase != BugHuntPhase.PLAYING) return
        val next = bh.selected.toMutableSet().apply { if (!add(index)) remove(index) }
        _state.update { it.copy(bugHunt = it.bugHunt.copy(selected = next)) }
    }

    /** 倒计时（由 Screen 每秒驱动）：归零自动结算。 */
    fun tickBugHunt() {
        val bh = _state.value.bugHunt
        if (bh.phase != BugHuntPhase.PLAYING) return
        val left = bh.secondsLeft - 1
        if (left <= 0) {
            finishBugHunt()
        } else {
            _state.update { it.copy(bugHunt = it.bugHunt.copy(secondsLeft = left)) }
        }
    }

    fun finishBugHunt() {
        val bh = _state.value.bugHunt
        if (bh.phase != BugHuntPhase.PLAYING) return
        val hits = bh.selected.count { it in bh.bugLines }
        val wrong = bh.selected.count { it !in bh.bugLines }
        val missed = bh.bugLines.count { it !in bh.selected }
        val perfect = hits > 0 && missed == 0 && wrong == 0
        val newStreak = if (perfect) bh.streak + 1 else 0
        val comboBonus = if (perfect) (newStreak - 1) * BugHunterState.COMBO_BONUS else 0
        val timeBonus = if (perfect) bh.secondsLeft else 0
        val score = (hits * BugHunterState.HIT_SCORE - wrong * BugHunterState.WRONG_PENALTY + timeBonus + comboBonus)
            .coerceAtLeast(0)
        _state.update {
            it.copy(
                bugHunt = it.bugHunt.copy(
                    phase = BugHuntPhase.DONE,
                    score = score,
                    streak = newStreak,
                    best = maxOf(it.bugHunt.best, score),
                    result = BugHuntResult(hits, wrong, missed, perfect, comboBonus),
                ),
            )
        }
    }
}
