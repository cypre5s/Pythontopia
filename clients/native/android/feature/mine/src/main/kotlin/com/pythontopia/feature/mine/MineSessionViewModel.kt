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
 * 富会话详情 ViewModel：加载 `GET /api/bug-mine/sessions/{id}`，按深度门控 reveal 卡片
 * （`.../cards/{cardId}/reveal`），原题重试（`.../retry-submit`）与 L5 同 KC 变式（`.../variant-submit`），
 * 以及关闭（`.../close?status=`）。每次写操作后重载会话以反映 depthLayer / status 变化。判题以服务端为准。
 */
@HiltViewModel
class MineSessionViewModel @Inject constructor(
    private val repository: MineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MineSessionUiState())
    val state: StateFlow<MineSessionUiState> = _state.asStateFlow()

    private var sessionId: Long = NO_SESSION

    /** 进入屏幕时按 sessionId 拉取详情；已加载同一会话则跳过。 */
    fun load(targetSessionId: Long) {
        if (targetSessionId <= 0L) {
            _state.update { it.copy(errorMessage = "无效的矿洞会话 ID") }
            return
        }
        if (_state.value.isLoading) return
        if (sessionId == targetSessionId && _state.value.session != null) return
        sessionId = targetSessionId
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch { fetchInto(resetCodes = true) }
    }

    fun updateRetryCode(code: String) {
        _state.update { it.copy(retryCode = code) }
    }

    fun updateVariantCode(code: String) {
        _state.update { it.copy(variantCode = code) }
    }

    /** 解锁卡片：深度不足后端会拒绝（409），失败写入 errorMessage 并刷新当前深度。 */
    fun revealCard(cardId: Long) {
        if (_state.value.isBusy) return
        _state.update { it.copy(revealingCardId = cardId, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.revealCard(sessionId, cardId) }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(revealingCardId = null) }
                    fetchInto(resetCodes = false)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            revealingCardId = null,
                            errorMessage = "解锁失败：${err.message ?: "深度不足或网络异常"}",
                        )
                    }
                },
            )
        }
    }

    /** 原题重试提交；通过即由后端转 RESOLVED，失败后端推进深度，二者均重载会话。 */
    fun submitRetry() {
        val session = _state.value.session ?: return
        if (!session.isOpen || _state.value.isBusy) return
        val code = _state.value.retryCode
        _state.update { it.copy(isSubmittingRetry = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.attempt(sessionId, code) }
            result.fold(
                onSuccess = { attempt ->
                    _state.update { it.copy(isSubmittingRetry = false, lastResult = attempt) }
                    fetchInto(resetCodes = false)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isSubmittingRetry = false,
                            errorMessage = "重试提交失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }

    /** L5 同 KC 变式提交；通过即 mastery_confirmed + RESOLVED。 */
    fun submitVariant() {
        val session = _state.value.session ?: return
        if (!session.canSubmitVariant || _state.value.isBusy) return
        val code = _state.value.variantCode
        _state.update { it.copy(isSubmittingVariant = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.variantAttempt(sessionId, code) }
            result.fold(
                onSuccess = { attempt ->
                    _state.update { it.copy(isSubmittingVariant = false, lastResult = attempt) }
                    fetchInto(resetCodes = false)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isSubmittingVariant = false,
                            errorMessage = "变式提交失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }

    /** 关闭会话（RESOLVED / ABANDONED）。 */
    fun close(status: String) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isClosing = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.close(sessionId, status) }
            result.fold(
                onSuccess = { detail ->
                    _state.update { it.copy(isClosing = false, session = detail, closed = true) }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isClosing = false,
                            errorMessage = "关闭会话失败：${err.message ?: "未知错误"}",
                        )
                    }
                },
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private suspend fun fetchInto(resetCodes: Boolean) {
        val result = runCatching { repository.sessionDetail(sessionId) }
        result.fold(
            onSuccess = { detail ->
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        session = detail,
                        retryCode = if (resetCodes) MineSessionUiState.DEFAULT_RETRY_CODE else current.retryCode,
                        variantCode = if (resetCodes) MineSessionUiState.DEFAULT_VARIANT_CODE else current.variantCode,
                    )
                }
            },
            onFailure = { err ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "会话加载失败：${err.message ?: "未知错误"}",
                    )
                }
            },
        )
    }

    private companion object {
        const val NO_SESSION = -1L
    }
}
