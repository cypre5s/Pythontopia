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
 * 记忆矿脉入口 ViewModel：并发拉取 `GET /api/bug-mine/echoes`（FSRS 到期）与 `GET /api/bug-mine/errorbook`，
 * 入矿（`POST /api/bug-mine/echoes`）成功后用 [MineEchoesUiState.pendingSessionId] 向 Screen 发一次性导航信号。
 * 判题与召回以服务端为准，失败写入 errorMessage，不做本地假数据。
 */
@HiltViewModel
class MineEchoesViewModel @Inject constructor(
    private val repository: MineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MineEchoesUiState())
    val state: StateFlow<MineEchoesUiState> = _state.asStateFlow()

    /** 刷新遗忘回声候选 + 错题本（顺序请求，任一失败整体置错）。 */
    fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                val echoes = repository.echoes()
                val errorBook = repository.errorBook()
                echoes to errorBook
            }
            result.fold(
                onSuccess = { (echoes, errorBook) ->
                    _state.update {
                        it.copy(isLoading = false, echoes = echoes, errorBook = errorBook)
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "矿脉数据加载失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }

    /** 入矿：打开遗忘回声 session，成功后置 [MineEchoesUiState.pendingSessionId] 触发导航。 */
    fun openEcho(kcCode: String) {
        if (_state.value.openingKcCode != null) return
        _state.update { it.copy(openingKcCode = kcCode, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.openEcho(kcCode) }
            result.fold(
                onSuccess = { session ->
                    _state.update {
                        it.copy(openingKcCode = null, pendingSessionId = session.sessionId)
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            openingKcCode = null,
                            errorMessage = "入矿失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }

    /** Screen 完成导航后清除一次性信号，避免重复跳转。 */
    fun consumePendingSession() {
        _state.update { it.copy(pendingSessionId = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
