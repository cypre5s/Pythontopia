package com.pythontopia.core.common

sealed interface AppError {
    val code: String
    val message: String
}

data class ApiError(
    override val code: String,
    override val message: String,
    val httpStatus: Int? = null,
) : AppError

data class NetworkError(
    override val code: String = "NETWORK_ERROR",
    override val message: String,
) : AppError

data class UnknownError(
    override val message: String,
    override val code: String = "UNKNOWN",
) : AppError
