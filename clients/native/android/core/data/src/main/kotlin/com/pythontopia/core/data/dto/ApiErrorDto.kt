package com.pythontopia.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorDto(
    val code: String,
    val message: String,
    val timestamp: String? = null,
    val path: String? = null,
)
