package com.pythontopia.core.network

object NetworkConfig {
    /** 由构建类型注入：debug=http://47.120.29.62:18080/（公网 ECS），release=HTTPS 域名（见 build.gradle.kts）。 */
    val DEFAULT_BASE_URL: String = BuildConfig.BASE_URL
    const val TIMEOUT_SECONDS: Long = 30L
    const val LLM_TIMEOUT_SECONDS: Long = 60L

    /**
     * 规范化测试人员手填的后端地址，便于直接喂给 OkHttp/Retrofit：
     *  - 缺协议时补 `http://`（局域网联调多为明文）；
     *  - 末尾补 `/`（Retrofit baseUrl 约定）。
     *
     * 返回 `null` 表示输入为空/全空白，调用方应回退到 [DEFAULT_BASE_URL]。
     */
    fun normalizeBaseUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val withScheme = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
