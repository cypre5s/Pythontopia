package com.pythontopia.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object RetrofitFactory {

    /**
     * @param baseUrl 初始 baseUrl（Retrofit 构造期使用）。
     * @param baseUrlResolver 非空时挂接 [HostSelectionInterceptor]，每次请求按其返回值改写
     *   scheme/host/port，实现运行时切换后端而无需重建（局域网联调）。
     * @param onUnauthorized 携带 token 的请求收到 401 时回调（清理会话）。
     */
    fun create(
        baseUrl: String,
        tokenProvider: () -> String? = { null },
        enableLogging: Boolean = true,
        baseUrlResolver: (() -> String)? = null,
        onUnauthorized: () -> Unit = {},
    ): Retrofit {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        val okhttp = OkHttpClient.Builder()
            .connectTimeout(NetworkConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .also { builder ->
                // 运行时主机切换：在鉴权前改写 scheme/host/port（免重建单例）。
                if (baseUrlResolver != null) {
                    builder.addInterceptor(HostSelectionInterceptor(baseUrlResolver))
                }
            }
            .addInterceptor(AuthInterceptor(tokenProvider, onUnauthorized))
            .also { builder ->
                if (enableLogging) {
                    builder.addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okhttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
