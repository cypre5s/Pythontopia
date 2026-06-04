package com.pythontopia.core.network

object NetworkConfig {
    /** 由构建类型注入：debug=http://10.0.2.2:18080/，release=HTTPS 域名（见 build.gradle.kts）。 */
    val DEFAULT_BASE_URL: String = BuildConfig.BASE_URL
    const val TIMEOUT_SECONDS: Long = 30L
    const val LLM_TIMEOUT_SECONDS: Long = 60L
}
