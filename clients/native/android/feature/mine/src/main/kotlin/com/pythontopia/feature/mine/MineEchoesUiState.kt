package com.pythontopia.feature.mine

/**
 * 矿洞入口（遗忘回声 + 错题本）UI 状态。
 * [pendingSessionId] 为打开会话后的一次性导航信号，由 Screen 消费后调用 [MineEchoesViewModel.consumePendingSession]。
 */
data class MineEchoesUiState(
    val isLoading: Boolean = false,
    val echoes: List<EchoCandidate> = emptyList(),
    val errorBook: List<ErrorBookItem> = emptyList(),
    val openingKcCode: String? = null,
    val pendingSessionId: Long? = null,
    val errorMessage: String? = null,
) {
    /** 既无回声也无错题（且非加载中）时展示空态引导。 */
    val isEmpty: Boolean
        get() = !isLoading && echoes.isEmpty() && errorBook.isEmpty()
}
