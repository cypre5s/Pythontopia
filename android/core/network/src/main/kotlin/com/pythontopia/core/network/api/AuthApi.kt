package com.pythontopia.core.network.api

import com.pythontopia.core.data.dto.LoginRequest
import com.pythontopia.core.data.dto.LoginResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @POST("api/auth/register")
    suspend fun register(@Body req: LoginRequest): LoginResponse
}
