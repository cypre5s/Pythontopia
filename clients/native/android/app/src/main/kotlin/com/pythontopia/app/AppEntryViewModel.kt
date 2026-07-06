package com.pythontopia.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythontopia.core.network.SessionState
import com.pythontopia.feature.auth.AuthDestination
import com.pythontopia.feature.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 冷启动会话恢复：决定首屏 [AuthDestination.HOME] 或 [AuthDestination.LOGIN]；
 * 解析在 IO 线程，期间 [startDestination] 为 null 显示启动占位。
 */
@HiltViewModel
class AppEntryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionState: SessionState,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val destination = withContext(Dispatchers.IO) {
                if (authRepository.restoreValidSession() != null) {
                    AuthDestination.HOME
                } else {
                    AuthDestination.LOGIN
                }
            }
            _startDestination.value = destination
        }
    }

    /** 退出登录：清空令牌与游客标记（调用方随后导航回登录页）。 */
    fun logout() {
        authRepository.clearSession()
        sessionState.isGuest = false
    }
}
