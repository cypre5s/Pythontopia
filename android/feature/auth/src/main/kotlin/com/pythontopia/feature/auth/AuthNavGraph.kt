package com.pythontopia.feature.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Android-R4 Auth 导航图。
 *
 * 3 个 route：
 *  - [AuthDestination.LOGIN]：用户名密码登录（已有）。
 *  - [AuthDestination.REGISTER]：账号注册（已有，R-4.4 接入真实 API）。
 *  - [AuthDestination.BIOMETRIC_UNLOCK]：冷启动且盘上 token 未过期时的生物识别快速通道（R-4.5 新增）。
 *
 * `HOME` 是导航外的"目标 graph"，不属于本图自己负责的 route——保留是为了让调用方
 * 用 `popUpTo(LOGIN) { inclusive = true }` 时仍能拿到字面量常量，避免硬编码。
 */
object AuthDestination {
    const val LOGIN: String = "login"
    const val REGISTER: String = "register"
    const val BIOMETRIC_UNLOCK: String = "biometric_unlock"
    const val HOME: String = "home"
}

fun NavGraphBuilder.authGraph(navController: NavController) {
    composable(AuthDestination.LOGIN) {
        LoginScreen(
            onLoginSuccess = {
                navController.navigate(AuthDestination.HOME) {
                    popUpTo(AuthDestination.LOGIN) { inclusive = true }
                }
            },
        )
    }
    composable(AuthDestination.REGISTER) {
        RegisterScreen(
            onRegisterSuccess = { _ -> navController.popBackStack() },
            onBackToLogin = { navController.popBackStack() },
        )
    }
    composable(AuthDestination.BIOMETRIC_UNLOCK) {
        BiometricUnlockScreen(
            onUnlocked = {
                navController.navigate(AuthDestination.HOME) {
                    popUpTo(AuthDestination.BIOMETRIC_UNLOCK) { inclusive = true }
                }
            },
            onFallbackToPassword = {
                navController.navigate(AuthDestination.LOGIN) {
                    popUpTo(AuthDestination.BIOMETRIC_UNLOCK) { inclusive = true }
                }
            },
        )
    }
}

/**
 * 生物识别解锁屏：内联实现，避免新增专属 Screen 文件膨胀。
 *
 * 进入即触发一次 [BiometricGate.unlockTokens]。任何失败/取消都把用户送回登录页，
 * 保证不会卡死在解锁页（部分 OEM 取消回调会延迟到下一帧）。
 */
@Composable
internal fun BiometricUnlockScreen(
    onUnlocked: () -> Unit,
    onFallbackToPassword: () -> Unit,
    viewModel: BiometricUnlockViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf(false) }

    LaunchedEffect(activity) {
        if (activity == null) {
            onFallbackToPassword()
            return@LaunchedEffect
        }
        if (attempted) return@LaunchedEffect
        attempted = true
        viewModel.attemptUnlock(
            activity = activity,
            onResult = { result ->
                when (result) {
                    BiometricGate.UnlockResult.Success -> onUnlocked()
                    BiometricGate.UnlockResult.Cancelled -> onFallbackToPassword()
                    is BiometricGate.UnlockResult.Error -> {
                        errorMessage = result.message
                    }
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("生物识别解锁", style = MaterialTheme.typography.headlineMedium)
        CircularProgressIndicator()
        Text(
            "请在系统弹窗中完成指纹或人脸识别。",
            style = MaterialTheme.typography.bodyMedium,
        )
        errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onFallbackToPassword) { Text("改用密码登录") }
        }
    }
}

/**
 * 生物识别页面的极简 ViewModel。
 *
 * 只暴露一个 `attemptUnlock` 方法——回调式 API 与 [BiometricGate] 保持一致，
 * 避免把 BiometricPrompt 的 listener 翻译成 Flow 引入额外的生命周期复杂度。
 *
 * 与 [AuthRepository] 解耦：解锁本身不涉及网络，只是让 UI 决定下一步该走 Hub 还是 Login；
 * 真正的 token 恢复在 [AuthRepository.restoreFromDisk] 里完成，由调用方在 MainActivity 启动时调用。
 */
@HiltViewModel
class BiometricUnlockViewModel @Inject constructor(
    private val biometricGate: BiometricGate,
) : ViewModel() {

    fun attemptUnlock(
        activity: FragmentActivity,
        onResult: (BiometricGate.UnlockResult) -> Unit,
    ) {
        biometricGate.unlockTokens(activity = activity, callback = onResult)
    }
}

/**
 * 沿 [ContextWrapper] 链向上查找 [FragmentActivity]。
 *
 * androidx.activity 1.10 才提供 `LocalActivity`，当前 BOM 仍在 1.9.3，
 * Compose 拿到的 `LocalContext` 可能是 `ContextThemeWrapper`，
 * 手动 unwrap 是 1.9.x 下兼容的解法。
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
