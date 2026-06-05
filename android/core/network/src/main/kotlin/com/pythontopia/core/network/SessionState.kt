package com.pythontopia.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程级会话模式标记。
 *
 * - [isGuest] = true：用户走「游客快速体验」进入，没有令牌；后端受限功能应**优雅降级**
 *   （显示"登录后解锁"而非报错），客户端可玩的部分（像素小镇探索、对话演示）照常。
 * - 真实登录 / 会话恢复时由 [com.pythontopia.core.network.AuthTokenHolder] 侧重置为 false。
 *
 * 与 [AuthTokenHolder] 同为进程内单例，跨 feature 共享（`:feature:auth` 设值，`:feature:hub` 读值）。
 */
@Singleton
class SessionState @Inject constructor() {
    @Volatile
    var isGuest: Boolean = false

    private val _sessionExpired = MutableStateFlow(false)

    /**
     * 会话被动失效信号（由 401 触发，见 [NetworkModule] 接线）。
     *
     * `:app` / `:feature:auth` 可观察它执行 `:core:network` 够不到的那部分清理：
     * 清空磁盘 TokenStorage（归 `:feature:auth`）并导航回登录页。网络层自身只负责
     * 清掉内存 token（见 [AuthTokenHolder]）+ 在此广播信号，避免反向依赖 feature。
     */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    /** 由 [NetworkModule] 在收到 401 时调用：重置为登出态并广播失效信号。 */
    fun markSessionExpired() {
        isGuest = false
        _sessionExpired.value = true
    }

    /** UI 消费完失效信号后复位，避免重复导航。 */
    fun acknowledgeSessionExpired() {
        _sessionExpired.value = false
    }
}
