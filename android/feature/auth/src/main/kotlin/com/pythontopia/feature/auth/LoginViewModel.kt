package com.pythontopia.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChange(v: String) {
        _state.update { it.copy(username = v) }
    }

    fun onPasswordChange(v: String) {
        _state.update { it.copy(password = v) }
    }

    fun login() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            // TODO Android-R3: 接 AuthApi.login(LoginRequest(username, password))
            delay(LOGIN_STUB_DELAY_MS)
            _state.update { it.copy(isLoading = false, success = true) }
        }
    }

    private companion object {
        const val LOGIN_STUB_DELAY_MS = 500L
    }
}
