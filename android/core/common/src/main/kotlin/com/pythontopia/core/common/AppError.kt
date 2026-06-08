package com.pythontopia.core.common

/** 应用层统一错误；[code] 供分类与日志，[message] 为用户可读文案。 */
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
