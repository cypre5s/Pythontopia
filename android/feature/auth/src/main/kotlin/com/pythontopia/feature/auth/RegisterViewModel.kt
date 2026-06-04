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
 * Android-R4.4 注册视图模型。
 *
 * 三个字段：
 *  - `username`（必填，≥ 3 字符）
 *  - `password`（必填，≥ 8 字符，含字母+数字；与 `AUTH_PASSWORD_WEAK` 后端规则一致）
 *  - `displayName`（可选，前端裁剪 trim 后空白即视为不填）
 *
 * 成功后 `success = true`，由调用方（[RegisterScreen]）触发 `popBackStack()` 回登录页，
 * 同时把用户名通过 `prefilledUsername` 向上回传，供登录页 prefill。
 */
data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
    val prefilledUsername: String? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onUsernameChange(v: String) {
        _state.update { it.copy(username = v, errorMessage = null) }
    }

    fun onPasswordChange(v: String) {
        _state.update { it.copy(password = v, errorMessage = null) }
    }

    fun onDisplayNameChange(v: String) {
        _state.update { it.copy(displayName = v, errorMessage = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.isLoading) return
        val localError = validate(current)
        if (localError != null) {
            _state.update { it.copy(errorMessage = localError) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, success = false) }
            val outcome = repository.register(
                RegisterRequest(
                    username = current.username.trim(),
                    password = current.password,
                    displayName = current.displayName.trim().takeIf { it.isNotEmpty() }
                        ?: current.username.trim(),
                ),
            )
            _state.update {
                when (outcome) {
                    is AuthOutcome.Success -> it.copy(
                        isLoading = false,
                        success = true,
                        errorMessage = null,
                        prefilledUsername = current.username.trim(),
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

    private fun validate(state: RegisterUiState): String? {
        val username = state.username.trim()
        if (username.length < MIN_USERNAME) return "用户名至少 $MIN_USERNAME 个字符"
        if (state.password.length < MIN_PASSWORD) return "密码至少 $MIN_PASSWORD 位"
        if (!state.password.any { it.isLetter() } || !state.password.any { it.isDigit() }) {
            return "密码需同时包含字母和数字"
        }
        return null
    }

    private companion object {
        const val MIN_USERNAME: Int = 3
        const val MIN_PASSWORD: Int = 8
    }
}
