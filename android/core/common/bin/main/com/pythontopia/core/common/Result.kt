package com.pythontopia.core.common

/** 异步操作结果：Success / Failure / Loading。 */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Failure(val error: AppError) : Result<Nothing>
    data object Loading : Result<Nothing>
}
