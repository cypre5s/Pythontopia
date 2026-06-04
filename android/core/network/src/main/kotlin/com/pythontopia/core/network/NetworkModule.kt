package com.pythontopia.core.network

import com.pythontopia.core.network.api.AuthApi
import com.pythontopia.core.network.api.BootstrapApi
import com.pythontopia.core.network.api.CalendarApi
import com.pythontopia.core.network.api.NpcDialogApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    fun provideRetrofit(tokenHolder: Provider<AuthTokenHolder>): Retrofit =
        RetrofitFactory.create(
            baseUrl = NetworkConfig.DEFAULT_BASE_URL,
            tokenProvider = { tokenHolder.get().get() },
            // 仅 debug 打 HTTP 日志（BASIC，不含令牌）；release 关闭避免 logcat 信息外泄。
            enableLogging = BuildConfig.DEBUG,
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
