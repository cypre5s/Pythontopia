package com.pythontopia.feature.workshop

/**
 * 百晓工坊意图路由 UI 状态。
 * [pendingDispatch] 为一次性导航信号，Screen 消费后调用 [IntentViewModel.consumeDispatch]。
 * [history] 保留最近若干次分类（最新在前），便于导航离开后回看上一次意图与理由。
 */
data class IntentUiState(
    val input: String = "",
    val isRouting: Boolean = false,
    val lastResponse: IntentRouteResponse? = null,
    val pendingDispatch: IntentDispatch? = null,
    val history: List<IntentRouteResponse> = emptyList(),
    val errorMessage: String? = null,
) {
    /** 可发送：输入非空白且当前没有进行中的分类。 */
    val canRoute: Boolean get() = input.isNotBlank() && !isRouting

    companion object {
        const val MAX_HISTORY = 8
    }
}
