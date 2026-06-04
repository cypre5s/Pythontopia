package com.pythontopia.core.network

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
}
