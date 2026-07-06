package com.pythontopia.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.net.HttpURLConnection

/**
 * 鉴权拦截器：
 *  - 有 token 时为请求加 `Authorization: Bearer <token>`；
 *  - 当**携带 token 的请求**收到 401 时回调 [onUnauthorized]，让上层清理会话
 *    （清空内存 token + 通过 SessionState 广播失效，接线见 [NetworkModule]）。
 *
 * 无 token（游客）时的 401 仅表示"需要登录"，**不**触发会话清理，避免误清。
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit = {},
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        if (token != null && response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            onUnauthorized()
        }
        return response
    }
}
