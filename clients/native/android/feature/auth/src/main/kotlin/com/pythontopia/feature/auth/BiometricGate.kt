package com.pythontopia.feature.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-R4.5 生物识别闸门。
 *
 * 与 `docs/clients/android/02-shared-state-contract.md` §8.1 / `01-feature-port-checklist.md` §4.1 对齐：
 *  - 优先用 `BIOMETRIC_STRONG`（Class 3，硬件级密钥绑定）。
 *  - 若设备只支持 `BIOMETRIC_WEAK`（部分 MIUI/OneUI ROM），允许降级，否则走密码二次输入。
 *
 * 设计：
 *  - 单例 `@Singleton`，无内部可变状态：[unlockTokens] 每次都创建新的 [BiometricPrompt]，
 *    避免回调与 Activity 生命周期发生跨次复用。
 *  - 回调用普通函数引用，调用方可在 ViewModel 里翻成 StateFlow，
 *    不强制依赖 coroutine——某些 OEM 的 callback 不在主线程，避免在桥层引入 dispatcher。
 *  - 接收 [FragmentActivity]（任意 `ComponentActivity` 都可向上转换）：
 *    androidx.biometric.BiometricPrompt 的构造器签名硬性要求 `FragmentActivity`。
 *    `:app` MainActivity 已经是 `FragmentActivity`，调用端可直接传入。
 */
@Singleton
class BiometricGate @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 设备生物识别可用性枚举。
     *  - [AVAILABLE_STRONG] / [AVAILABLE_WEAK] 可以直接弹 prompt。
     *  - [NONE_ENROLLED] 设备支持但用户未录入指纹/人脸：UI 引导去系统设置。
     *  - [NO_HARDWARE] / [HARDWARE_UNAVAILABLE] 隐藏入口，直接走密码登录。
     */
    enum class Availability {
        AVAILABLE_STRONG,
        AVAILABLE_WEAK,
        NONE_ENROLLED,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        UNKNOWN,
    }

    sealed interface UnlockResult {
        data object Success : UnlockResult
        /** 用户主动取消（点击 "使用密码登录" 或返回键）；UI 应回到 Login 页。 */
        data object Cancelled : UnlockResult
        /** 系统报告的错误码 + 文案；UI 用 [message] 渲染提示。 */
        data class Error(val errorCode: Int, val message: String) : UnlockResult
    }

    fun availability(): Availability {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE_STRONG
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Availability.HARDWARE_UNAVAILABLE
            else -> classifyWeakFallback(manager)
        }
    }

    private fun classifyWeakFallback(manager: BiometricManager): Availability {
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE_WEAK
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.NO_HARDWARE
            else -> Availability.UNKNOWN
        }
    }

    /**
     * 弹出系统生物识别面板；结果通过 [callback] 回报。
     *
     * @param activity 必须是 `FragmentActivity` 或其子类（`:app` MainActivity 已满足）。
     * @param title 面板标题；不传时使用默认中文文案。
     * @param subtitle 副标题；可选。
     * @param negativeButtonText "取消/使用密码" 按钮文案；点击后回 [UnlockResult.Cancelled]。
     */
    fun unlockTokens(
        activity: FragmentActivity,
        title: String = DEFAULT_TITLE,
        subtitle: String = DEFAULT_SUBTITLE,
        negativeButtonText: String = DEFAULT_NEGATIVE,
        callback: (UnlockResult) -> Unit,
    ) {
        val availability = availability()
        val allowedAuthenticators = when (availability) {
            Availability.AVAILABLE_STRONG -> BiometricManager.Authenticators.BIOMETRIC_STRONG
            Availability.AVAILABLE_WEAK -> BiometricManager.Authenticators.BIOMETRIC_WEAK
            else -> {
                callback(UnlockResult.Error(ERROR_UNAVAILABLE, "当前设备未启用生物识别"))
                return
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(allowedAuthenticators)
            .setConfirmationRequired(false)
            .build()

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    callback(UnlockResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val isCancel = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (isCancel) {
                        callback(UnlockResult.Cancelled)
                    } else {
                        callback(UnlockResult.Error(errorCode, errString.toString()))
                    }
                }

                // onAuthenticationFailed 是单次比对失败（指纹错纹路），
                // 系统会保留 prompt 等待重试；不向调用方上报，避免抖动。
            },
        )

        prompt.authenticate(promptInfo)
    }

    private companion object {
        const val DEFAULT_TITLE: String = "解锁 Pythontopia"
        const val DEFAULT_SUBTITLE: String = "使用生物识别快速登录"
        const val DEFAULT_NEGATIVE: String = "使用密码登录"

        /** 自定义错误码：BiometricManager 返回不可用时的统一标记，避开系统码区间。 */
        const val ERROR_UNAVAILABLE: Int = -100
    }
}
