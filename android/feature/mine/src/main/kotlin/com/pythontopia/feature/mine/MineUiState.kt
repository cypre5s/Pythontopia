package com.pythontopia.feature.mine

/** Bug 矿洞 UI 状态；[currentCard]/[currentLayer] 为派生 getter，避免序列化扩展字段。 */
data class MineUiState(
    val isLoading: Boolean = false,
    val session: MineSession? = null,
    val cards: List<MineCard> = emptyList(),
    val cardIdx: Int = 0,
    val userCode: String = "",
    val lastResult: MineAttempt? = null,
    val failureCount: Int = 0,
    val showEscapeDialog: Boolean = false,
    val npcHintRevealed: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
) {

    /** 当前卡；未加载会话或 cards 为空时为 null。 */
    val currentCard: MineCard?
        get() = cards.getOrNull(cardIdx)

    /** 当前层 1..5；优先 [currentCard.layer]，否则 [MineSession.currentLayer]，默认 1。 */
    val currentLayer: Int
        get() = currentCard?.layer
            ?: session?.currentLayer
            ?: 1

    /** 最近一次提交是否通过。 */
    val lastAttemptPassed: Boolean
        get() = lastResult?.status == MineAttempt.STATUS_PASS

    companion object {
        /** 单卡连续失败达此次数弹 [MineEscapeDialog]。 */
        const val ESCAPE_THRESHOLD = 3

        /** 副本层数 B1..B5。 */
        const val TOTAL_LAYERS = 5
    }
}
