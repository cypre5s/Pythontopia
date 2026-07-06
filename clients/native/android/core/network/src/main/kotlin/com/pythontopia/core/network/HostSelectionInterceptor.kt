package com.pythontopia.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 把每个请求的 scheme/host/port 改写为 [baseUrlResolver] 返回的当前生效地址，
 * 从而支持运行时切换后端（局域网联调）而无需重建单例 Retrofit/OkHttp。
 *
 * 仅改写 scheme/host/port，保留原始 path/query —— 因此覆盖地址应为主机根地址
 * （形如 `http://host:port/`）；Retrofit 注解里的相对 path 保持不变。
 * 若解析失败（地址非法），回退为不改写，交由原 baseUrl 兜底。
 */
class HostSelectionInterceptor(
    private val baseUrlResolver: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val target = baseUrlResolver().toHttpUrlOrNull()
        val rewritten = if (target != null) {
            val newUrl = request.url.newBuilder()
                .scheme(target.scheme)
                .host(target.host)
                .port(target.port)
                .build()
            request.newBuilder().url(newUrl).build()
        } else {
            request
        }
        return chain.proceed(rewritten)
    }
}
