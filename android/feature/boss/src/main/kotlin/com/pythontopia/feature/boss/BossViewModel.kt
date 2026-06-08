package com.pythontopia.feature.boss

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 章末 BOSS：选章 → 4 阶段逐关出题、写码、服务端判题；全过 → VICTORY。 */
@HiltViewModel
class BossViewModel @Inject constructor(
    private val repository: BossRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BossUiState())
    val state: StateFlow<BossUiState> = _state.asStateFlow()

    fun selectChapter(chapter: Int) {
        _state.update {
            it.copy(phase = BossPhase.BATTLE, chapter = chapter, stage = 1, result = null)
        }
        loadStage()
    }

    fun loadStage() {
        val s = _state.value
        _state.update {
            it.copy(loading = true, error = null, result = null, problem = null, code = BossUiState.STARTER_CODE)
        }
        viewModelScope.launch {
            repository.fetchProblem(s.chapter, s.stage)
                .onSuccess { problem ->
                    _state.update {
                        it.copy(problem = problem, loading = false, startEpochMs = System.currentTimeMillis())
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = "召唤 BOSS 失败：${e.message ?: "题目服务不可用"}")
                    }
                }
        }
    }

    fun onCodeChange(code: String) {
        _state.update { it.copy(code = code) }
    }

    fun submit() {
        val s = _state.value
        val problem = s.problem ?: return
        if (s.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        val elapsedSec = ((System.currentTimeMillis() - s.startEpochMs) / 1000L).toInt().coerceAtLeast(0)
        viewModelScope.launch {
            repository.submitAttempt(s.chapter, s.stage, problem.problemId, s.code, elapsedSec)
                .onSuccess { result ->
                    _state.update { it.copy(submitting = false, result = result) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(submitting = false, error = "提交失败：${e.message ?: "判题服务不可用"}")
                    }
                }
        }
    }

    /** 末阶段通关 → VICTORY，否则 stage+1 并 loadStage。 */
    fun advance() {
        val s = _state.value
        if (s.result?.passed != true) return
        if (s.isLastStage) {
            _state.update { it.copy(phase = BossPhase.VICTORY) }
        } else {
            _state.update { it.copy(stage = it.stage + 1) }
            loadStage()
        }
    }

    /** 未通过时清 result，保留题目重试。 */
    fun retry() {
        _state.update { it.copy(result = null) }
    }

    fun reset() {
        _state.update { BossUiState() }
    }
}
