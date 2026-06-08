package com.pythontopia.feature.endless

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 无尽练习：随机抽题 → 判题 → 累计连胜/已解/积分。 */
@HiltViewModel
class EndlessViewModel @Inject constructor(
    private val repository: EndlessRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EndlessUiState())
    val state: StateFlow<EndlessUiState> = _state.asStateFlow()

    private var kcPool: List<String> = emptyList()

    init {
        bootstrap()
    }

    private fun bootstrap() {
        _state.update { it.copy(loadingProblem = true, error = null) }
        viewModelScope.launch {
            kcPool = repository.kcCodes()
            if (kcPool.isEmpty()) {
                _state.update { it.copy(loadingProblem = false, error = "无法加载题库（请确认已登录且后端在线）") }
            } else {
                drawProblem()
            }
        }
    }

    fun nextProblem() {
        _state.update {
            it.copy(loadingProblem = true, error = null, result = null, liveStatus = null, code = EndlessUiState.STARTER_CODE)
        }
        viewModelScope.launch { drawProblem() }
    }

    private suspend fun drawProblem() {
        if (kcPool.isEmpty()) {
            kcPool = repository.kcCodes()
        }
        var picked: EndlessProblemDto? = null
        var tries = 0
        while (picked == null && tries < MAX_DRAW_TRIES && kcPool.isNotEmpty()) {
            tries++
            val kc = kcPool.random()
            val problems = repository.problems(kc)
            if (problems.isNotEmpty()) {
                picked = problems.random()
            }
        }
        if (picked == null) {
            _state.update { it.copy(loadingProblem = false, error = "暂时抽不到题目，请稍后重试") }
        } else {
            _state.update { it.copy(loadingProblem = false, problem = picked, error = null) }
        }
    }

    fun onCodeChange(code: String) {
        _state.update { it.copy(code = code) }
    }

    fun submit() {
        val s = _state.value
        val problem = s.problem ?: return
        if (s.submitting) return
        _state.update { it.copy(submitting = true, liveStatus = EndlessStatus.PENDING, error = null, result = null) }
        viewModelScope.launch {
            val created = runCatching { repository.submit(problem.problemId, s.code) }.getOrElse { e ->
                _state.update { it.copy(submitting = false, liveStatus = null, error = "提交失败：${e.message ?: "网络异常"}") }
                return@launch
            }
            runCatching {
                repository.pollStatus(created.id).collect { dto ->
                    _state.update { it.copy(liveStatus = dto.status) }
                    if (dto.isTerminal) applyTerminal(dto, problem)
                }
            }.onFailure { e ->
                _state.update { it.copy(submitting = false, error = "判题轮询失败：${e.message ?: "网络异常"}") }
            }
            _state.update { if (it.submitting && it.result == null) it.copy(submitting = false) else it }
        }
    }

    private fun applyTerminal(dto: EndlessSubmissionDto, problem: EndlessProblemDto) {
        _state.update {
            val passed = dto.status.isSuccess
            val newStreak = if (passed) it.streak + 1 else 0
            it.copy(
                submitting = false,
                result = dto,
                attempts = it.attempts + 1,
                solved = if (passed) it.solved + 1 else it.solved,
                streak = newStreak,
                bestStreak = maxOf(it.bestStreak, newStreak),
                score = if (passed) it.score + problem.reward else it.score,
            )
        }
    }

    private companion object {
        const val MAX_DRAW_TRIES = 8
    }
}
