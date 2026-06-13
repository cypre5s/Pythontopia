package com.pythontopia.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 后端 baseUrl 的运行时单一真源。
 *
 * 解析优先级：DataStore 覆盖值（测试人员手填，见 [NetworkHostStore]）
 * > [NetworkConfig.DEFAULT_BASE_URL]（即 BuildConfig.BASE_URL，debug 默认公网 `http://47.120.29.62:18080/`）。
 *
 * - [current] 由 [HostSelectionInterceptor] 在每次请求时（OkHttp 后台线程）读取，
 *   因此修改覆盖值后**无需重建/重启**即可对后续请求生效。
 * - 首次访问时惰性、同步地从 DataStore 水合一次（只读一个 key，发生在后台线程），
 *   保证测试人员重启后第一笔请求也能命中持久化的覆盖地址。
 */
@Singleton
class BaseUrlProvider @Inject constructor(
    private val hostStore: NetworkHostStore,
) {
    @Volatile
    private var override: String? = null

    @Volatile
    private var hydrated: Boolean = false

    /** 当前生效的 baseUrl（始终带末尾 `/`）。 */
    fun current(): String {
        if (!hydrated) hydrate()
        return override ?: NetworkConfig.DEFAULT_BASE_URL
    }

    /** 是否存在测试人员设置的覆盖值（用于设置页展示"默认/已覆盖"）。 */
    fun hasOverride(): Boolean {
        if (!hydrated) hydrate()
        return override != null
    }

    /**
     * 设置/清除运行时覆盖值并持久化。传 `null`/空白即清除（回退默认）。
     * 立即更新内存态，使后续请求即时生效（无需重建 Retrofit）。
     */
    suspend fun setOverride(rawUrl: String?) {
        val normalized = NetworkConfig.normalizeBaseUrl(rawUrl)
        override = normalized
        hydrated = true
        hostStore.write(normalized)
    }

    /** 观察持久化的覆盖值（供设置 UI），`null` 表示未覆盖。 */
    fun overrideFlow(): Flow<String?> = hostStore.overrideFlow

    @Synchronized
    private fun hydrate() {
        if (hydrated) return
        override = runCatching { runBlocking { hostStore.read() } }
            .getOrNull()
            ?.let(NetworkConfig::normalizeBaseUrl)
        hydrated = true
    }
}
