package com.pythontopia.core.common

/** UI 加载态：Idle / Loading / Success / Error（区别于 [Result] 的请求层模型）。 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val error: AppError) : UiState<Nothing>
}
