package com.pythontopia.core.network

import android.content.Context
import com.pythontopia.core.network.api.AuthApi
import com.pythontopia.core.network.api.BootstrapApi
import com.pythontopia.core.network.api.CalendarApi
import com.pythontopia.core.network.api.NpcDialogApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import retrofit2.Retrofit

/**
 * 全局 JWT 持有者：登录后 [setToken]，后续 Retrofit 调用经 [AuthInterceptor] 携带 Bearer。
 * 单进程内 demo 用 in-memory 即可；持久化（EncryptedSharedPreferences）在 Android-R4 已规划，
 * Android-R12 demo 阶段不强制启用。
 */
@Singleton
class AuthTokenHolder @Inject constructor() {
    @Volatile private var token: String? = null

    fun get(): String? = token
    fun setToken(value: String?) { token = value }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTokenHolder(): AuthTokenHolder = AuthTokenHolder()

    @Provides
    @Singleton
    fun provideNetworkHostStore(@ApplicationContext context: Context): NetworkHostStore =
        NetworkHostStore(context)

    @Provides
    @Singleton
    fun provideRetrofit(
        tokenHolder: Provider<AuthTokenHolder>,
        baseUrlProvider: BaseUrlProvider,
        sessionState: SessionState,
    ): Retrofit =
        RetrofitFactory.create(
            // 初始 baseUrl 取默认（避免 DI 构造期触发 DataStore 阻塞读）；
            // 真正生效地址由 HostSelectionInterceptor 在请求时（后台线程）解析。
            baseUrl = NetworkConfig.DEFAULT_BASE_URL,
            tokenProvider = { tokenHolder.get().get() },
            // 仅 debug 打 HTTP 日志（BASIC，不含令牌）；release 关闭避免 logcat 信息外泄。
            enableLogging = BuildConfig.DEBUG,
            // 运行时主机覆盖：测试人员改地址后，后续请求即时切换、无需重建/重启。
            baseUrlResolver = { baseUrlProvider.current() },
            onUnauthorized = {
                // 401：清空内存 token，避免后续请求继续带过期 Bearer；
                // 通过 SessionState 广播失效（磁盘 token 清理 + 导航由 :feature:auth 观察执行）。
                tokenHolder.get().setToken(null)
                sessionState.markSessionExpired()
            },
        )

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideBootstrapApi(retrofit: Retrofit): BootstrapApi = retrofit.create(BootstrapApi::class.java)

    @Provides
    @Singleton
    fun provideNpcDialogApi(retrofit: Retrofit): NpcDialogApi = retrofit.create(NpcDialogApi::class.java)

    @Provides
    @Singleton
    fun provideCalendarApi(retrofit: Retrofit): CalendarApi = retrofit.create(CalendarApi::class.java)
}
