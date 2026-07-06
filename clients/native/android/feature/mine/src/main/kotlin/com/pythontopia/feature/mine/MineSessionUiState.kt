package com.pythontopia.feature.mine

/**
 * 富会话详情（5 张 reveal 门控深度卡 + 重试 + L5 变式）UI 状态。
 * [depthLayer] 为派生 getter，单一真相在 [session]。
 */
data class MineSessionUiState(
    val isLoading: Boolean = false,
    val session: MineSessionDetail? = null,
    val retryCode: String = DEFAULT_RETRY_CODE,
    val variantCode: String = DEFAULT_VARIANT_CODE,
    val isSubmittingRetry: Boolean = false,
    val isSubmittingVariant: Boolean = false,
    val revealingCardId: Long? = null,
    val isClosing: Boolean = false,
    val lastResult: MineAttempt? = null,
    val closed: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 当前下探深度 1..5。 */
    val depthLayer: Int get() = session?.depthLayer ?: 1

    /** 最近一次提交是否通过。 */
    val lastAttemptPassed: Boolean get() = lastResult?.status == MineAttempt.STATUS_PASS

    /** 任意提交/解锁/关闭进行中：用于禁用按钮。 */
    val isBusy: Boolean
        get() = isSubmittingRetry || isSubmittingVariant || isClosing || revealingCardId != null

    companion object {
        const val DEFAULT_RETRY_CODE = "# 重新按题意实现一次（不看提示先裸重试）：\n"
        const val DEFAULT_VARIANT_CODE = "# L5 同 KC 变式题：独立完成以确认迁移掌握\n"
    }
}
