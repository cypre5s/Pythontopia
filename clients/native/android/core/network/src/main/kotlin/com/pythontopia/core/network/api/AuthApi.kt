package com.pythontopia.core.network.api

import com.pythontopia.core.data.dto.LoginRequest
import com.pythontopia.core.data.dto.LoginResponse
import retrofit2.http.Body
import retrofit2.http.POST

/** 认证 API：登录与注册，返回 JWT（由调用方写入 [com.pythontopia.core.network.AuthTokenHolder]）。 */
interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @POST("api/auth/register")
    suspend fun register(@Body req: LoginRequest): LoginResponse
}
