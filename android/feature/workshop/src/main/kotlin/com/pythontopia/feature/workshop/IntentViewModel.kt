package com.pythontopia.feature.workshop

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
 * 意图路由 ViewModel：一句话 → `POST /api/intent/route` → 计算 [IntentDispatch] 并发一次性导航信号。
 * REVIEW 走记忆矿脉回声，ASK/UNKNOWN 走导师，其余走工坊生成（映射见 [toDispatch]）。失败写入 errorMessage。
 */
@HiltViewModel
class IntentViewModel @Inject constructor(
    private val repository: IntentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(IntentUiState())
    val state: StateFlow<IntentUiState> = _state.asStateFlow()

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    /** 分类并分派；空白或进行中直接忽略。 */
    fun route() {
        val message = _state.value.input.trim()
        if (message.isEmpty() || _state.value.isRouting) return
        _state.update { it.copy(isRouting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.route(message) }
            result.fold(
                onSuccess = { response ->
                    _state.update { current ->
                        current.copy(
                            input = "",
                            isRouting = false,
                            lastResponse = response,
                            pendingDispatch = response.toDispatch(),
                            history = (listOf(response) + current.history).take(IntentUiState.MAX_HISTORY),
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isRouting = false,
                            errorMessage = "意图识别失败：${err.message ?: "网络异常"}",
                        )
                    }
                },
            )
        }
    }

    /** Screen 完成导航后清除一次性信号。 */
    fun consumeDispatch() {
        _state.update { it.copy(pendingDispatch = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
