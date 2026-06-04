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
 * 应用启动入口决策：冷启动时尝试恢复加密持久化的会话，决定首屏去 [AuthDestination.HOME]
 * 还是 [AuthDestination.LOGIN]，避免每次启动都强制重登。
 *
 * 解析在 IO 线程（EncryptedSharedPreferences 首访是磁盘 + Keystore I/O，不能卡主线程）；
 * 解析期间 [startDestination] 为 null，UI 显示极简启动占位。
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
