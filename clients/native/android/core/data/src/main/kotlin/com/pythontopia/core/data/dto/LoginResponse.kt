package com.pythontopia.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String,
    val expiresAt: String,
    val user: UserDto,
)
