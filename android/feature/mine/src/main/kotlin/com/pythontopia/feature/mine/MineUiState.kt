package com.pythontopia.feature.mine

/**
 * Bug 矿洞副本 UI 状态。
 *
 * 单状态对象，字段≥6，按 `docs/android/01-feature-port-checklist.md` §4.10 推荐其实应走
 * MVI；Beta 首发为了开发速度仍使用 MVVM + `StateFlow<MineUiState>` 单 state 写法，
 * R-13 接 LibGDX 装饰层联动时再升级到 `MineIntent` sealed class。
 *
 * 状态分类：
 * - 流程态：[isLoading] / [isCompleted]
 * - 数据态：[session] / [cards] / [cardIdx] / [lastResult]
 * - 输入态：[userCode]
 * - 异常／教练干预：[failureCount] / [showEscapeDialog] / [npcHintRevealed]
 * - 反馈态：[errorMessage]
 *
 * 派生量（[currentCard] / [currentLayer]）用 `val` getter 暴露，让 Compose 端不必每帧
 * 手动 `cards.getOrNull(cardIdx)`，又避免序列化扩展字段被错误持久化。
 *
 * @property isLoading 网络请求进行中；同一时刻 startSession / submit / next 共用此标志，
 *   因为它们互斥（用户在编辑代码时不会触发副本切换）。
 * @property session 当前会话快照；null 表示尚未 startSession 或开新副本前已 reset。
 * @property cards 本次会话所有可见卡片（首屏 + 已通过 next 拉到的候补），按 layer 升序。
 * @property cardIdx [cards] 中当前展示的卡索引；越界由 [currentCard] 安全 fallback 为 null。
 * @property userCode 玩家在编辑器里的最新代码；切卡时从 [MineCard.snippet] 重置。
 * @property lastResult 最近一次 attempt 的结果；用于 Submit 按钮下方的反馈区显示。
 * @property failureCount 当前卡的连续失败次数；通过后清零，达到 [ESCAPE_THRESHOLD] 弹 [MineEscapeDialog]。
 * @property showEscapeDialog 是否弹出 ESCAPE 对话框；UI 唯一来源，避免散落在多处。
 * @property npcHintRevealed 是否已召唤"勤勉小柒"NPC 给提示；防止重复 buff 同张卡。
 * @property isCompleted 整个副本通关 / 主动撤退到结束；UI 据此切换到完成态视图。
 * @property errorMessage 网络或业务错误的可读文案；UI 用红字 chip 渲染，下次操作清空。
 */
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

    /** 当前展示的卡；session 未加载或 cards 为空时为 null。 */
    val currentCard: MineCard?
        get() = cards.getOrNull(cardIdx)

    /**
     * 当前所在层（1..5），优先取 [currentCard.layer]，无卡时退回到 [MineSession.currentLayer]，
     * 都没有则默认 1。让顶部 [MineLayerProgress] 在空态下仍有合理高亮位置。
     */
    val currentLayer: Int
        get() = currentCard?.layer
            ?: session?.currentLayer
            ?: 1

    /** 当前卡是否刚通过 attempt（用于隐藏 Submit 按钮、显示「下一卡」按钮）。 */
    val lastAttemptPassed: Boolean
        get() = lastResult?.status == MineAttempt.STATUS_PASS

    companion object {
        /** 单卡连续失败达到此次数 → 自动弹 [MineEscapeDialog]，对齐卡牌库 §8.1。 */
        const val ESCAPE_THRESHOLD = 3

        /** 副本固定 5 层（B1..B5），UI 进度条用。 */
        const val TOTAL_LAYERS = 5
    }
}
