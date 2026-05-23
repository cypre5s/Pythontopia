package com.pythontopia.feature.arena

/**
 * Arena 模块 UI 状态。
 *
 * 状态字段较多（≥ 4），按 ADR-001 §1 决策 10 的口径其实应走 MVI；但本期 Beta 首发
 * "简化版"约束不要复杂的 PVP 实时同步，所以保留 MVVM + `StateFlow<ArenaUiState>` 的
 * 单状态对象写法，等真正接 PVP / 异步判题轮询时再升级到 Intent/Reducer。
 *
 * **状态总览**：
 * - 加载态：[isLoadingSession] / [isLoadingLeaderboard] / [isSubmitting]
 * - 数据态：[session] / [leaderboard] / [lastSubmission]
 * - 交互态：[selectedTab] / [currentProblemIdx] / [codeBuffers]
 * - 反馈态：[errorMessage]
 * - 派生量：[myRank]（从 leaderboard 中挑 [myUserId] 的行，避免 UI 层重复 find）
 *
 * `lastSubmission` 故意按"全局最近一次"语义保存，而不是 `Map<problemId, Submission>`，
 * 这样 [ProblemRunner] 不论切换到哪一题都可以在底部展示"我上一次的判题结果"，让玩家
 * 知道刚才到底有没有提交过；切题不清空，但重新提交会覆盖。
 *
 * **演示账号约定**：当前 Android demo 没接登录态时，[myUserId] 取常量 104L（与
 * [ArenaRepository] DEMO_LEADERBOARD 中"我（演示账号）"那行 userId 对齐），方便排行榜
 * 能高亮"我"的位置。R-12 Beta+1 接 SessionRepository 后改为从 JWT 读取真实 userId。
 */
data class ArenaUiState(
    val isLoadingSession: Boolean = false,
    val isLoadingLeaderboard: Boolean = false,
    val isSubmitting: Boolean = false,
    val session: ArenaSession? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val lastSubmission: Submission? = null,
    val selectedTab: ArenaTab = ArenaTab.TODAY,
    val currentProblemIdx: Int = 0,
    val codeBuffers: Map<Long, String> = emptyMap(),
    val myUserId: Long = DEMO_USER_ID,
    val errorMessage: String? = null,
) {
    /** 当前选中的题目；session 未加载或题目列表为空时返回 null。 */
    val currentProblem: ArenaProblem?
        get() = session?.problems?.getOrNull(currentProblemIdx)

    /**
     * 当前选中题目的代码缓冲；优先取玩家已编辑的版本，否则回退到 [ArenaProblem.starterCode]。
     * 让玩家在 tab 间切换 / 提交后回到题目时保留编辑内容。
     */
    val currentCode: String
        get() {
            val problem = currentProblem ?: return ""
            return codeBuffers[problem.problemId] ?: problem.starterCode
        }

    /** "我"在当前排行榜中的排名条目；找不到（如还没提交）返回 null。 */
    val myRank: LeaderboardEntry?
        get() = leaderboard.firstOrNull { it.userId == myUserId }

    companion object {
        const val DEMO_USER_ID = 104L
    }
}

/**
 * 屏幕顶部的两个 tab。
 *
 * 用 enum 而非 sealed class，因为 tab 集合是封闭的（演示版只有两个），且不需要每个 tab
 * 自带 payload。Compose 端可直接 `ArenaTab.values()` 渲染 TabRow。
 */
enum class ArenaTab(val label: String) {
    TODAY("今日赛事"),
    LEADERBOARD("积分榜"),
}
