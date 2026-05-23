package com.pythontopia.feature.auth

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
 * Android-R4.1 重构：去掉 500ms `delay` 桩，改为通过 [AuthRepository] 真实调用后端。
 *
 * UI 状态保持向后兼容：仍是 `username / password / isLoading / errorMessage / success`，
 * 让现有 [LoginScreen] 不需要改动。新增 `lastSession`，给上层导航判断 role 路由用。
 */
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
    val lastSession: SessionSnapshot? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChange(v: String) {
        _state.update { it.copy(username = v, errorMessage = null) }
    }

    fun onPasswordChange(v: String) {
        _state.update { it.copy(password = v, errorMessage = null) }
    }

    /**
     * 表单提交。前置校验只兜底空字符串，复杂规则（用户名长度 / 密码强度）由后端 4001 错误码承担，
     * 这样前后端只有一份真相，不会出现 "前端通过、后端拒绝" 的撕裂。
     */
    fun login() {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(errorMessage = "请输入用户名和密码") }
            return
        }
        if (current.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, success = false) }
            val outcome = repository.login(
                LoginRequest(username = current.username.trim(), password = current.password),
            )
            _state.update {
                when (outcome) {
                    is AuthOutcome.Success -> it.copy(
                        isLoading = false,
                        success = true,
                        errorMessage = null,
                        lastSession = outcome.session,
                    )
                    is AuthOutcome.Failure -> it.copy(
                        isLoading = false,
                        success = false,
                        errorMessage = outcome.error.message,
                    )
                }
            }
        }
    }

    /**
     * 用于错误提示出现后，用户改了输入再触发登录前先消除红字。
     * UI 层 LoginScreen 当前没显式调用，但保留接口给后续 R-4.6 Compose UI Test 用。
     */
    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
