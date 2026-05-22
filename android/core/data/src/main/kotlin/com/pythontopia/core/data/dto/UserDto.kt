package com.pythontopia.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val role: String,
    val displayName: String? = null,
)
